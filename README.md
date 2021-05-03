# KRAAL

KRAAL (Knowledge and Reasoning Awesome Algorithms with Limits) is a set of Graql scripts and ontologies aiming at transforming [Grakn](https://grakn.ai/) into a RDF triple store with integrated RDFS (and in the future OWL) reasoner.

In addition, some "glue" code to import RDF into Grakn is provided.


## How does it work?

Grakn does not support [RDF](https://www.w3.org/TR/rdf-concepts/) stack natively. It comes with its own query language (Graql);
in addition, it is a strongly typed knowledge graph that needs a schema definition before data can be inserted.

The idea around KRAAL is quite simple. The provided Graql scripts define the below concepts (among others used for support):

1. `rdf-node` which represents a node in a RDF graph. This is an abstract string attribute that has some sub-concepts:
	1. `rdf-non-literal` An abstract string attribute that has some concrete sub-concepts:
		1. `rdf-IRI` a IRI.
		2. `rdf-blank-node` a blank node, notice each blank node has a unique identifier as its value.
	2. `rdf-literal` a literal. Literals have an optional data type and language tag attached.

2. `rdf-triple` is a ternary relationship representing an RDF triple.

In addition, scripts create rules to represent semantic of predicates in a way that triples are inferred and materialized accordingly to existing facts.

Lastly, some Java code is provided to import RDF triples from various file format into Grakn, using the above concepts.

In the end, once the above schema and rules are imported, Grakn can be used as an RDF triple store (with triples stored as `rdf-triple` relations) with an integrated reasoner (provided by the semantic rules).


## Prerequisites

KRAAL has been developed and tested with Grakn Core version 2.0.2 on Windows 10 (but nothing should be specific to Windows).


### Java

Grakn 2.0.2 needs Java 11 or higher to run. The Java code in this repository is also targeted to Java 11.

Please make sure you have [downloaded](https://www.oracle.com/java/technologies/javase/jdk11-archive-downloads.html) and installed Java 11 on your machine.
Both Oracle Java and OpenJDK will work.

The `grakn.bat` file in Grakn distribution below, calls Java to start the server. If your default JRE is not compatible with Grakn, you can edit this file to point to the correct JRE.


### Grakn 

1. Grakn Core (which comes with an integrated text-based console)
can be downloaded and installed from the [Grakn website](https://grakn.ai/download#core).

2. [Grakn Workbase](https://grakn.ai/download#workbase) is a graphical UI that can be useful
  in querying Grakn. You can download it as an executable file and simply executing it with
  double clicking.

Please see the [quickstart](https://docs.grakn.ai/docs/running-grakn/install-and-run)
on the Grakn website to familiarize a bit with Grakn and its console.


## RdfImporter

This is a small Java utility to import RDF files into Grakn. It is provided as a `RdfImporter.jar` under the `Graql` folder.
Notice that, in order to use the tool, you must have created the required RDF support (that is schema and rules) 
as explained in next section below.

```
java -jar RdfImporter.jar -k <arg> -f <arg> [-u <arg>] [-s <arg>] file1 [file2] ...

	-f <arg>   Format of input file.
	-k <arg>   Database to use for importing.
	-s <arg>   "Batch" size; perform this many insertions before committing a
				transaction. Higher values might speed up execution, as long
				as you have enough memory.
	-u <arg>   Base IRI to use for nodes without a base.

	Recognized file formats:
	

		RDF:	RDF/XML
		NT:		N-Triples file format (.nt)
		TTL:	Turtle file format (.ttl)
		TTLS:	Turtle* (TurtleStar) file format (.ttls)
		N3:		N3/Notation3 file format (.n3)
		TRIX:	TriX
		TRIG:	TriG file format (.trig)
		TRIGS:	TriG* (TriGStar) file format (.trigs)
		BRF:	A binary RDF format (.brf)
		NQ:		N-Quads file format (.nq)
		JSONLD:	JSON-LD file format (.jsonld)
		RJ:		RDF/JSON file format (.rj)
		RDFA:	RDFa file format (.xhtml)
		HDT:	HDT file format (.hdt)
```


## Graql scripts 

The folder `Graql` contains Graql script needed to implement required concepts and rules.
In addition, it contains RDF files with vocabularies.


### RDF and RDF Schema support 

The file `rdf.gql` is a Graql schema that adds support for RDF. 
In addition, it adds some rules so to implement [RDF Schema 1.1](https://www.w3.org/TR/rdf-schema/)
semantics.
		
1. To import the schema you can use Grakn console. the below shows how to create a blank databased 
named `rdf`and import the schema there. Please refer to Grakn console documentation for details.

	```
	Welcome to Grakn Console. You are now in Grakn Wonderland!
	Copyright (C) 2021 Grakn Labs

	> database create rdf
	Database 'rdf' created
	> transaction rdf schema write
	rdf::schema::write> source rdf.gql
	Concepts have been defined
	rdf::schema::write> commit
	Transaction changes committed
	```
		
2. 	Then you must import RDF and RDF Schema vocabularies into the `rdf` database using `RdfImporter` import utility as shown below.
In this example we assume `.jar` and vocabularies are in the same folder from where you 
run the command.

	```java -jar RdfImporter.jar -k rdf -f TTL 22-rdf-syntax-ns.ttl rdf-schema.ttl```


## Graql Editor

To create Graql files (schema and rules) we suggest using [Notepad++](https://notepad-plus-plus.org/downloads/):
The file `Notepad++/graql.xml` is a language definition that adds syntax coloring for Graql to Notepad++.


## How to release

Export `RdfImporter` from Eclipse as executable `.jar` file inside `Graql` folder.
The main class is `io.github.mzattera.semanticweb.kraal.RdfImporter`.

