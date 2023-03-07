# enflame

A visual query interface for [CANDEL](https://www.parkerici.org/research-project/candel-data-analysis-platform/) and other graph databases.


# Theory of operation

Enflame is started up with a configuration file that specifies (among other things):
- an Alzabo schema
- a data source
- a query generation method

# Configuration

See [sample config](resources/candel-config.edn)

# Development Mode

## Requirements

- leiningen
- A CANDEL endpoint or other data source


## To run locally from source:

Create the file `deploy/launch-config.edn` according to your needs (see the sample configs in `resources`). Then:

    lein launch

This will compile the front-end, lauch the back-end, and open a browser windoe.

## Documentation generation

Requirements: emacs and pandoc

Run the script

    doc/build-guide.sh
	
This builds the guide and tutorial in HTML format.



## Library

Back end is Google Datastore.


