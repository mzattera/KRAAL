# KRAAL

KRAAL (Knowledge and Reasoning Awesome Algorithms and L? - OK, I made this up) is a set of Graql scrpts and ontologies aiming at transforming [Grakn](https://grakn.ai/) into a RDF triple store with integrated OWL reasoner.

In addition, some "glue" code to import RDF into Grakn is provided.


## How does it work?

Grakn does not support [RDF](https://www.w3.org/TR/rdf-concepts/) stack natively. It comes with its own query language (Graql);
in addition, it is a strongly typed knowledge graph that needs a schema definition before data can be inserted.

The idea arounf KRAAL is quite simple. The provided Graql scripts define the below concepts:

1. `rdf-node` which represents a node in a RDF graph. This is an abstract string attribute that has some concrete sub-concepts:
	1. `rdf-blank-node` a blank node, notice each blank node has a unique identifier.
	2.  `rdf-uri-reference` a URI.
	3.  `rdf-literal` a literal. Literals have an optonal data type and language tag attached.

2. `rdf-triple` is a ternary relationship representing an RDF triple.

In addition, scripts create rules to represent semantic of predicates in a way that triples are inferred and materialized accordingly to existing facts.

Lastly some Java code is provided to import RDF triples from various file format into Grakn, using the above concepts.

In the end, once that the above schema and rules ar created, Grakn can be used as an RDF triple store (with tr
iples stored as `rdf-triple` relations)
with an integrated reasoner (provided by the semantic rules).


## Prerequisites

KRAAL has been developed and tested with Grakn Core version 1.8.4 on Windows 10 (but nothing should be specific to Windows).


### Java

Grakn needs Java 1.8 to run. The Java code in this repository is also targeted to Java 1.8.

Please make sure you have [downloaded](https://www.oracle.com/java/technologies/javase-jre8-downloads.html) and installed Java 1.8 on yor machine.


### Grakn Core

Grakn core (which comes with an intrated text-basde console)
can be downloaded and installed from the [Grakn website](https://grakn.ai/download#core).

1. Download the `.zip` file and unzip it in a folder of your choice (we will call it `<grakn>`).

2. Change `<grkn>\grakn.bat` (or similar script) so it calls the 1.8 JRE installed above,
  instead of default one that can be an unsupported  version.
  You can then use this file to start both the Grakn server and the console. 

3. [Grakn Workbase](https://grakn.ai/download#workbase) is a graphical UI that can be useful
  in querying Grakn. You can download it as an executable file and simply executing it with
  double clicking.

Please see the [quickstart](https://docs.grakn.ai/docs/running-grakn/install-and-run)
on the Grakn website to familiarize a bit with Grakn and its console.

## RdfImporter ##

This is a small Java utility to import RDF files into Grakn. It is provided as a `.jar` file under the `Graql` folder.
Notice that, in order to use the tool, you must have created the required RDF support (that is schema and rules) 
as explained below.

```
java -jar RdfImport.jar io.github.mzattera.semanticweb.kraal.RdfImporter -k <arg> -f <arg> [-u <arg>] [-s <arg>] file1 [file2] ...

	-f <arg>   Format of input file.
	-k <arg>   Key space to use for importing.
	-s <arg>   "Batch" size; perform this many insertions before committing a
				transaction. Higher values might speed up execution, as long
				as you have enough memory.
	-u <arg>   Base URI to use for nodes without a base.

	Recognized file formats:
	

		RDF:	RDF/XML
		NT:	N-Triples file format (.nt)
		TTL:	Turtle file format (.ttl)
		TTLS:	Turtle* (TurtleStar) file format (.ttls)
		N3:	N3/Notation3 file format (.n3)
		TRIX:	TriX
		TRIG:	TriG file format (.trig)
		TRIGS:	TriG* (TriGStar) file format (.trigs)
		BRF:	A binary RDF format (.brf)
		NQ:	N-Quads file format (.nq)
		JSONLD:	JSON-LD file format (.jsonld)
		RJ:	RDF/JSON file format (.rj)
		RDFA:	RDFa file format (.xhtml)
		HDT:	HDT file format (.hdt)
```

## Graql scripts 

The folder Graql contains Graql script needed to implement required concepts and rules.
In addition, it contains RDF files with vocabularies.


### RDF and RDF Schema support 

The file `rdf.gql` is a Graql schema that adds support for RDF. 
In addition, it adds some rules so to implement [RDF Schema 1.1](https://www.w3.org/TR/rdf-schema/)
semantics.
		
1. To import the schema you can use Grakn console:

	```grakn console -k <keyspace> -f rdf.gql```
		
This will import the schema into the provided keyspace (creating it if necessary).
		
2. 	Then you must import RDF and RDF Schema vocabularies using RdfImport import utility as explained below.
In th example we assume you will use the "rdf" keyspace and that the `.jar` and vocabularies are in the same folderfrom where you 
run the command.

```java -jar RdfImport.jar io.github.mzattera.semanticweb.kraal.RdfImporter -k rdf -f TTL 22-rdf-syntax-ns.ttl rdf-schema.ttl```
	
	
## Graql Editor

To create graql files (schema and rules) we suggeest using [Notepad++](https://notepad-plus-plus.org/downloads/):
The file `Notepad++/graql.xml`is a language definition that adds syntax coloring for Graql to Notepad++.


## How to release

Export RdfImporter from Eclipse as executable `.jar` file inside `Graql` folder.

