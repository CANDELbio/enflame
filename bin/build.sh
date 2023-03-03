# Build Docker image

# TODO parameterize properly

export VERSION=$(git rev-parse --short HEAD)
echo Building version $VERSION

bin/build-doc.sh

# get latest version of schema (TODO: is this a good idea?)
# doesn't seem to actually work
# git submodule update --init --recursive

# TODO needs rethinking
# Run Alzabo to build schemas
# cd alzabo; lein with-profile prod do clean, run documentation candel "*", cljsbuild once; cd ..

lein uberjar

# TODO abort if lein fails, duh. 

# Build Docker image

docker build -t cbio .

# verify
# docker run -p 8080 cbio


docker tag cbio:latest 733151965047.dkr.ecr.us-east-1.amazonaws.com/cbio:latest

# Upload to AWS repository

aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 733151965047.dkr.ecr.us-east-1.amazonaws.com

docker push 733151965047.dkr.ecr.us-east-1.amazonaws.com/cbio:latest
