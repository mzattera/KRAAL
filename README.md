# KRAAL

KRAAL (Knowledge and Reasoning Awesome Algorithms and L? - OK, I made this up) is a set of Graql rules and ontologies aiming at transforming [Grakn](https://grakn.ai/) into a RDF triple store with integrated OWL reasoner.

In addition, some "glue" code to import RDF into Grakn is provided.


## Prerequisites

KRAAL has been developed and tested with Grakn Core version 1.8.4 on Windows 10 (but nothing should be specific to Windows).

### Java

Grakn needs Java 1.8 to run. The Java code in this repository is also targeted to Java 1.8.

Please make sure you have [downloaded](https://www.oracle.com/java/technologies/javase-jre8-downloads.html) and installed Java 1.8 on yor machine.


### Grakn Core

Grakn core (which comes with an intrated text-basde console)
can be downloaded and installed from the [Grakn website](https://grakn.ai/download#core).

1. 	Download the .zip file and unzip it in a folder of your choice (we will call it ```<grakn>```).

2. 	Change ```<grkn>\grakn.bat``` (or similar script) so it calls the 1.8 JRE installed above,
  instead of default one that can be an unsupported  version.
  You can use this file to start both the Grakn server and the console (see 

3. 	[Grakn Workbase](https://grakn.ai/download#workbase) is a graphical UI that can be useful
  in querying Grakn. You can download it as an executable file and simply executing it with
  double clicking.

Please see the [quickstart](https://docs.grakn.ai/docs/running-grakn/install-and-run)
on the Grakn website to familiarize a bit with Grakn and its console.

# RDF support

Grakn does not support [RDF](https://www.w3.org/TR/rdf-concepts/) stack natively.

It comes with its own query language (graql); in addition, it is a strongly typed knowledge graph
that needs a schema definition before data can be inserted.

To circumvent this problem and provide RDF support follow the below steps.

	1. 	The file rdf.gql is a Grakn schema that adds support for RDF.
		In addition, it adds some rules so to implement [RDF Schema 1.1](https://www.w3.org/TR/rdf-schema/)
		semantics on top of Grakn.
		
		To import the schema you can use Grakn console:
		
			´´´grakn console -k <keyspace> -f rdf.gql´´´
		
		This will import the schema into the provided keyspace (creating it if necessary).
		
	2. 	There is a small tool that can be used to import RDF files into Grakn; this is provided as 
		Java program (RdfImport.jar)
		As first use of the tool, we will import RDF and RDF Schema definitions;
		these are provided as ´´´22-rdf-syntax-ns.ttl´´´ and ´´´rdf-schema.ttl´´´ Turtle files respectively.
		To import them run the below command
		(we assume the ´´´RdfImport.jar´´´ and the ´´´.ttl´´´ files are all in the current folder).
		
		´´´java -jar RdfImport.jar -k rdf -f TTL 22-rdf-syntax-ns.ttl rdf-schema.ttl´´´
		
		You can run ´´´java -jar RdfImport.jar´´´ to get a list of progrma options.
	
Now you can use RdfImport.jar to import any RDF file into Grakn; it will use the rules we define
to infer new triples, based on RDF Schema semantics.

The file ´´´Eclipse - GraknRDF´´´ is an Eclips workbench with RdfImport sources.


# Graql Editor

To create graql files (schama and rules) we suggeest using Notepad++ which is available from Infosys 
software center. The file ´´´graql.xml´´´ is a language definition that adds syntax coloring for
graql to Notepad++.
