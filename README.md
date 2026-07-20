# rag-server

load dataset in this order:

SourceTransformer (this loads the basic format as input) -bootstrap before this repo should take over
then run:
DataInitializerMain
ChunkerMain
EmbedderMain
VectorStoreMain