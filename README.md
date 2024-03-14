# langchain4j-local-rag-sample

This is a simple RAG service running everything locally 
that uses Vespa or OpenSearch as the VectorStore and an ollama model 
for building embeddings and prompting.

## Runtime Requirements


### Ollama model running locally

Install [ollama](https://ollama.ai/download)

The [default configuration](src/main/resources/rag-sample.conf) is using the [gemma:7b model](https://ollama.ai/library/mistral):
```shell
ollama pull gemma:7b
```

To list your local ollama models:
```shell
ollama list

# For more details on the models do:
curl -s localhost:11434/api/tags | jq .
```

### (option 1) Vespa 


#### Start Vespa cluster

You need to start a Vespa version 8 cluster:

```shell
docker run --detach \
  --name vespa \
  --hostname vespa-tutorial \
  --publish 8080:8080 \
  --publish 19071:19071 \
  --publish 19092:19092 \
  --publish 19050:19050 \
  vespaengine/vespa:8
```

Note: the 19050 port is not absolutely necessary, but has a nice
[status page](http://localhost:19050/clustercontroller-status/v1/llm) 
for the Vespa cluster once you have your Vespa doc-types in place.

#### Deploy Vespa application
Install the vespa-cli if needed:
```shell
brew install vespa-cli
```

Run from the root of this repo:
```shell
vespa deploy --wait 300 vespa
```
If you used the above docker command to expose the 19050
port then you can monitor the Cluster status on this page:
http://127.0.0.1:19050/clustercontroller-status/v1/llm


#### Stopping Vespa

To kill (and delete all data from) the Vespa cluster just:
```shell
docker rm -f vespa
```


#### Delete all Vespa docs
```shell
# Delete all books
curl -X DELETE \
  "http://localhost:8080/document/v1/embeddings/books/docid?selection=true&cluster=llm"

# Delete all news
curl -X DELETE \
  "http://localhost:8080/document/v1/embeddings/news/docid?selection=true&cluster=llm"
```


### (Option 2) OpenSearch [WIP]

Follow [the instructions](https://opensearch.org/docs/latest/install-and-configure/install-opensearch/docker/#run-opensearch-in-a-docker-container) 
to set up a single node OpenSearch server with docker.

Using docker-compose:
```shell
cd opensearch
wget https://raw.githubusercontent.com/opensearch-project/documentation-website/2.12/assets/examples/docker-compose.yml

# Setup your admin password
echo "OPENSEARCH_INITIAL_ADMIN_PASSWORD=$OPENSEARCH_INITIAL_ADMIN_PASSWORD" > .env

# Start the containers as detached daemons:
docker-compose up -d
```

Check that OpenSearch is up and running:
```shell
curl -ku "admin:$OPENSEARCH_INITIAL_ADMIN_PASSWORD" https://localhost:9200

# If the docker containers do not start then check the server logs:
docker logs opensearch-node1
```

Things that might go wrong above are:
- Not enough [strong admin password](https://github.com/opensearch-project/documentation-website/blob/6f779cef0c78efd3dc0f45f9dd30eee3339a65b4/_security/configuration/yaml.md#password-settings)
- Not setting [the sysctl limits](https://opensearch.org/docs/latest/install-and-configure/install-opensearch/docker/#important-host-settings) 

Please set the `OPENSEARCH_INITIAL_ADMIN_PASSWORD` env variable to a 
[strong password](https://github.com/opensearch-project/documentation-website/blob/6f779cef0c78efd3dc0f45f9dd30eee3339a65b4/_security/configuration/yaml.md#password-settings) 
as OpenSearch will not start otherwise.

Open http://localhost:5601 and login as admin with the `OPENSEARCH_INITIAL_ADMIN_PASSWORD` password you created above.

## BUILD

```shell
mvn clean package
```

## USAGE

```shell
# Populate the Vector store
./target/langchain4j-local-rag-sample-0.0.1-assembly/bin/rag-sample-create-embeddings.sh

# Chat 
./target/langchain4j-local-rag-sample-0.0.1-assembly/bin/rag-sample-cli.sh
```

### Call GRPC Service

```shell
# Start GRPC server
./target/langchain4j-local-rag-sample-0.0.1-assembly/bin/rag-sample-grpc-service.sh

# Call the service
grpcurl --plaintext -d '{"question": "What is the Foundation?"}' 127.0.0.1:4242 ragsample.RagSample.Ask
```