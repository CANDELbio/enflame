# enflame

A visual query interface for (CANDEL)[https://www.parkerici.org/research-project/candel-data-analysis-platform/] and other graph databases.


# Theory of operation

Enflame is started up with a configuration file that specifies (among other things):
- an Alzabo schema
- a data source
- a query generation method

# Configuration

See (sample config)[resources/candel-config.edn]

# Development Mode

## Requirements

leiningen
Some kind of data source


## To run locally from source:

Copy resources/candel-config.edn to deploy/candel-config.edn, filling out as appropriate

    lein launch

Browser should open to  http://localhost:1991

## Doc generation

Requirements: emacs and pandoc

Run the script

    bin/build-guide.sh
	
This builds the guide in both HTML format (for serving) and Markdown (for https://github.com/ParkerICI/candel-website)

## To deploy by hand

(1) Compile front and back end and build the Docker image:

    bin/build.sh

(2) Deploy

    bin/deploy.sh	


## Library

Back end is Google Datastore.


