# datomic-talk

Small and very basic example demonstrating using datomic to build a RESTful API.
Hopefully this can serve as a platform for future talks on topics like om.next, etc...


## Getting Started

1. start the application `lein repl` or `lein repl :headless` if you wish to connect a
   repl client
2. from the `user` namespace, at the repl, `(go)`
3. The slide show will be served at http://localhost:8080/index.html"
4. API endpoint will be running under http://localhost:8080/api/v0.1

### Files of inteterest

1. Connecting to the Database: `src/datomic_talk/components/datomic.clj`
2. Schema and seed fact files: `resources/schema.edn` and `resources/facts.edn`
3. Querying: `src/datomic_talk/query.clj`
  * I would not recommend following the pattern of bypassing the routing handler
    I have implemented here.  I wanted to see how generalized I could make the query
    system and I have essentially delegated the routing of queries to the interceptor chain.
  * I would, however, take away that you can build a _query context_ out of reusable parts
    to eliminate re-writing the same query.  This has the added benefit of keeping the data
    consistantly formatted.
4. Creating entities: `src/datomic_talk/post.clj
   * same caveats about routing as above
5. Updating entities: `src/datomic_talk/put.clj`
   * The interesting work about merging updates with existing entities in a way that allow us t     to allow for idiomatic retractions by explicitly passing nils for attributes in
     `src/datomic_talk/merge.clj`.
6. Maintaining schema invariants and data transforms: `src/datomic_talk/schema.clj`



## Configuration

To configure logging see config/logback.xml. By default, the app logs to stdout and logs/.
To learn more about configuring Logback, read its [documentation](http://logback.qos.ch/documentation.html).

## Links
* [Other examples](https://github.com/pedestal/samples)

