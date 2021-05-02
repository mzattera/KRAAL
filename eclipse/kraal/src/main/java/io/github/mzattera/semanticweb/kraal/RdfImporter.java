/**
 * 
 */
package io.github.mzattera.semanticweb.kraal;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.StatementCollector;

import grakn.client.Grakn;
import grakn.client.api.GraknClient;
import grakn.client.api.GraknOptions;
import grakn.client.api.GraknSession;
import grakn.client.api.GraknTransaction;
import grakn.client.api.answer.ConceptMap;
import graql.lang.Graql;
import graql.lang.pattern.variable.ThingVariable.Attribute;
import graql.lang.query.GraqlCompute.Path;
import graql.lang.query.GraqlInsert;
import io.github.mzattera.semanticweb.util.Utils;

/**
 * Imports RDF triples into Grakn. It assumes proper Grakn schema for RDF has
 * already been created.
 * 
 * @author Massimiliano "Maxi" Zattera
 *
 */
public final class RdfImporter implements Closeable {

	private static final String DEFAULT_BASE_URI = "http://io.github.mzattera.semanticweb/#";

	// We insert at maximum this number of triplets before performing a commit()
	private static final int DEFAULT_TRIPLES_PER_TRANSACTION = 1500;

	// TODO check if it is OK to get core.
	private static final GraknOptions QUERY_OPTIONS = GraknOptions.core().infer(false).explain(false).batchSize(1);

	// Client connected to the host
	private final GraknClient client;

	// Opened session to the host
	private final GraknSession session;

	// Resources being created, so we can create them only once.
	private final Set<String> createdResources = new HashSet<>();

	/**
	 * Creates a client to default (local) host.
	 * 
	 * @param db The database to connect to.
	 */
	public RdfImporter(String db) {
		this(Grakn.DEFAULT_ADDRESS, db);
	}

	/**
	 * Creates a client to given host (and port).
	 * 
	 * @param host The Grakn host:port to connect to.
	 * @param db   The database to connect to.
	 */
	public RdfImporter(String host, String db) {
		client = Grakn.coreClient(host);
		session = client.session(db, GraknSession.Type.DATA);
	}

	/**
	 * Imports an RDF file into Grakn.
	 * 
	 * @param fileName  Name of file with data.
	 * @param format    Format of the file (see Rio.createParser(format)).
	 * @param baseUri   Base URI for URIs without one.
	 * @param batchSize How many triples to import before a commit.
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	public void importFile(String fileName, RDFFormat format, String baseUri, int batchSize)
			throws FileNotFoundException, IOException, RDFParseException {

		System.out.print(fileName + ": 0.. ");

		createdResources.clear();

		// Reads all statements in a file into a list
		// TODO use event-based approach, so you can handle huge files.
		RDFParser rdfParser = Rio.createParser(format);
		List<Statement> statements = new ArrayList<>();
		rdfParser.setRDFHandler(new StatementCollector(statements));

		// TODO Encoding?
		File in = new File(fileName);
		if (!in.canRead()) throw new FileNotFoundException("Cannot access file: " + in.getCanonicalPath());
		try (InputStream is = new FileInputStream(fileName)) {
			rdfParser.parse(is, baseUri);
		} // close input stream

		int rows = 0;
		int tot = 0;

		// TODO can we reuse transaction? probably so....
		GraknTransaction writeTransaction = commitAndReopenTransaction(session, null);

		for (Statement s : statements) {

			Value sbj = s.getSubject();
			Value pred = s.getPredicate();
			Value obj = s.getObject();

			// Create resources to be later referenced by the relationship
			insert(sbj, writeTransaction);
			insert(pred, writeTransaction);
			insert(obj, writeTransaction);

			// Insert the triple
			// TODO Notice that if a relationship is inserted twice (e.g. from two files),
			// it gets duplicated.
			// On the other hand, RDF resources are attributes and therefore never
			// duplicated.
			GraqlInsert query = Graql
					.match(Graql.var("s").eq(sbj.stringValue()).isa("rdf-non-literal"),
							Graql.var("p").eq(pred.stringValue()).isa("rdf-IRI"),
							Graql.var("o").eq(Utils.toString(obj)).isa("rdf-node")) // This must be escaped
					.insert(Graql.var("t").rel("rdf-subject", "s").rel("rdf-predicate", "p").rel("rdf-object", "o")
							.isa("rdf-triple"));

			Stream<ConceptMap> inserted = writeTransaction.query().insert(query, QUERY_OPTIONS);
			if (inserted.count() != 1) {
				// TODO proper logging and handling
				System.out.println("\tS:\t" + Utils.toString(sbj) + "\t" + sbj.getClass().getName());
				System.out.println("\tP:\t" + Utils.toString(pred) + "\t" + pred.getClass().getName());
				System.out.println("\tO:\t" + Utils.toString(obj) + "\t" + obj.getClass().getName());
				throw new RuntimeException(inserted.count() + " rdf-triple were inserted.");
			}

			++tot;
			if (++rows >= batchSize) {
				writeTransaction = commitAndReopenTransaction(session, writeTransaction);
				System.out.print(tot + "... ");
				rows = 0;
			}
		} // for each RDF statement

		// close writeTransaction
		writeTransaction.commit();
		writeTransaction.close();
		System.out.println(tot + " <end>");
	}

	@Override
	public void close() {
		if ((session != null) && session.isOpen()) {
			try {
				session.close();
			} catch (Exception e) {
			}
		}
		if ((client != null) && client.isOpen()) {
			try {
				client.close();
			} catch (Exception e) {
			}
		}
	}

	private final static String MEMBER_PREFIX = "http://www.w3.org/1999/02/22-rdf-syntax-ns#_";

	/**
	 * Insert given value into Grakn knowledge graph, such that it can then be
	 * referenced in relationships (such as RDF triples).
	 * 
	 * @param v The value to insert.
	 */
	private void insert(Value v, GraknTransaction writeTransaction) {
		if (createdResources.contains(v.stringValue()))
			return;

		GraqlInsert query = null;
		if (v.isBNode()) {
			query = Graql.insert(Graql.var("x").eq(v.stringValue()).isa("rdf-blank-node"));
		} else if (v.isIRI()) {
			query = Graql.insert(Graql.var("x").eq(v.stringValue()).isa("rdf-IRI"));
		} else if (v.isLiteral()) {
			Literal l = (Literal) v;
			Attribute stmt = Graql.var("l").eq(Utils.toString(l)).isa("rdf-literal").has("rdf-datatype",
					l.getDatatype().stringValue());
			if (!l.getLanguage().equals(Optional.empty())) {
				stmt = stmt.has("rdf-language-tag", l.getLanguage().get());
			}
			query = Graql.insert(stmt);
		} else {
			// We should never get here
			throw new UnsupportedOperationException();
		}

		if (writeTransaction.query().insert(query, QUERY_OPTIONS).count() != 1)
			throw new RuntimeException(v.stringValue() + " not inserted.");

		createdResources.add(v.stringValue());

		if (v.isIRI()) {
			// Special handling is needed for rdfs:ContainerMembershipProperty
			String propertyURI = v.stringValue();
			if (propertyURI.startsWith(MEMBER_PREFIX) && (MEMBER_PREFIX.length() < propertyURI.length())) {
				try {
					Integer.parseInt(propertyURI.substring(MEMBER_PREFIX.length()));

					// At this point, we know propertyURI is like
					// rdf:_1, rdf:_2, rdf:_3 ...
					// This means it is an instance of rdf:member.
					// We need to specify this, as there is no other way to
					// easily implement semantic of rdf:_nnn properties.
					query = Graql
							.match(Graql.var("s").eq(v.stringValue()).isa("rdf-IRI"),
									Graql.var("p").eq("http://www.w3.org/2000/01/rdf-schema#subPropertyOf")
											.isa("rdf-IRI"),
									Graql.var("o").eq("http://www.w3.org/2000/01/rdf-schema#member").isa("rdf-IRI"))
							.insert(Graql.var("t").rel("rdf-subject", "s").rel("rdf-predicate", "p")
									.rel("rdf-object", "o").isa("rdf-triple"));

					if (writeTransaction.query().insert(query, QUERY_OPTIONS).count() != 1)
						throw new RuntimeException(v.stringValue() + " not properly marked as rdfs:memeber.");
				} catch (NumberFormatException e) {
				}
			}
		}
	}

	/**
	 * Commits current transaction (if any) and returns a new one to be used.
	 * 
	 * @param session Open session (db)
	 * @param t       Current transaction, if any, or null.
	 * @return
	 */
	private GraknTransaction commitAndReopenTransaction(GraknSession session, GraknTransaction t) {
		if (t != null) {
			t.commit();
			t.close();
		}

		return session.transaction(GraknTransaction.Type.WRITE);
	}

	public static void main(String[] args) {

		try {
			// Read parameters from command line
			Options cliOpt = new Options();
			cliOpt.addOption("k", true, "Database to use for importing.");
			cliOpt.addOption("f", true, "Format of input file.");
			cliOpt.addOption("u", true, "Base URI to use for nodes without a base.");
			cliOpt.addOption("s", true,
					"\"Batch\" size; perform this many insertions before committing a transaction. Higher values might speed up execution, as long as you have enough memory.");
			CommandLine cli = (new DefaultParser()).parse(cliOpt, args);

			String db = cli.getOptionValue("k");
			RDFFormat format = parseFormat(cli.getOptionValue("f"));
			List<String> files = cli.getArgList();
			if (db == null || format == null || files.size() < 1) {
				printUsage(cliOpt);
				System.exit(-1);
			}
			String baseUri = cli.getOptionValue("u");
			if (baseUri == null)
				baseUri = DEFAULT_BASE_URI;
			int batchSize = DEFAULT_TRIPLES_PER_TRANSACTION;
			try {
				if (cli.getOptionValue("s") != null)
					batchSize = Integer.parseInt(cli.getOptionValue("s"));
			} catch (NumberFormatException e) {
				printUsage(cliOpt);
				System.exit(-1);
			}

			// Import required files
			try (RdfImporter in = new RdfImporter(db)) {
				for (String file : files)
					in.importFile(file, format, baseUri, batchSize);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static RDFFormat parseFormat(String optionValue) {
		if (optionValue == null) {
			return null;
		}

		optionValue = optionValue.toUpperCase();
		if (optionValue.equals("RDF")) {
			// RDF/XML
			return RDFFormat.RDFXML;
		} else if (optionValue.equals("NT")) {
			// N-Triples file format (.nt).
			return RDFFormat.NTRIPLES;
		} else if (optionValue.equals("TTL")) {
			// Turtle file format (.ttl).
			return RDFFormat.TURTLE;
		} else if (optionValue.equals("TTLS")) {
			// Turtle* (TurtleStar) file format (.ttls)
			return RDFFormat.TURTLESTAR;
		} else if (optionValue.equals("N3")) {
			// N3/Notation3 file format (.n3)
			return RDFFormat.N3;
		} else if (optionValue.equals("TRIX")) {
			// TriX
			return RDFFormat.TRIX;
		} else if (optionValue.equals("TRIG")) {
			// TriG file format (.trig)
			return RDFFormat.TRIG;
		} else if (optionValue.equals("TRIGS")) {
			// TriG* (TriGStar) file format (.trigs)
			return RDFFormat.TRIGSTAR;
		} else if (optionValue.equals("BRF")) {
			// A binary RDF format (.brf)
			return RDFFormat.BINARY;
		} else if (optionValue.equals("NQ")) {
			// N-Quads file format (.nq)
			return RDFFormat.NQUADS;
		} else if (optionValue.equals("JSONLD")) {
			// JSON-LD file format (.jsonld)
			return RDFFormat.JSONLD;
		} else if (optionValue.equals("RJ")) {
			// RDF/JSON file format (.rj)
			return RDFFormat.RDFJSON;
		} else if (optionValue.equals("RDFA")) {
			// RDFa file format (.xhtml)
			return RDFFormat.RDFA;
		} else if (optionValue.equals("HDT")) {
			// HDT file format (.hdt)
			return RDFFormat.HDT;
		}

		return null;
	}

	private static void printUsage(Options cliOpt) {
		(new HelpFormatter()).printHelp(
				RdfImporter.class.getName() + " -k <arg> -f <arg> [-u <arg>] [-s <arg>] file1 [file2] ...", cliOpt);
		System.out.println("\nRecognized file formats:");
		System.out.println("\n\tRDF:\tRDF/XML");
		System.out.println("\tNT:\tN-Triples file format (.nt)");
		System.out.println("\tTTL:\tTurtle file format (.ttl)");
		System.out.println("\tTTLS:\tTurtle* (TurtleStar) file format (.ttls)");
		System.out.println("\tN3:\tN3/Notation3 file format (.n3)");
		System.out.println("\tTRIX:\tTriX");
		System.out.println("\tTRIG:\tTriG file format (.trig)");
		System.out.println("\tTRIGS:\tTriG* (TriGStar) file format (.trigs)");
		System.out.println("\tBRF:\tA binary RDF format (.brf)");
		System.out.println("\tNQ:\tN-Quads file format (.nq)");
		System.out.println("\tJSONLD:\tJSON-LD file format (.jsonld)");
		System.out.println("\tRJ:\tRDF/JSON file format (.rj)");
		System.out.println("\tRDFA:\tRDFa file format (.xhtml)");
		System.out.println("\tHDT:\tHDT file format (.hdt)");
	}
}
