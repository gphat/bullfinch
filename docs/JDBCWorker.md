# JDBC Worker #

The JDBC worker's configuration looks like this:

    {
        "workers" : [
            {
                "name" : "JDBC Worker",
                // The kestrel host we'll be connecting to
                "kestrel_host" : "127.0.0.1",
                // The kestrel port
                "kestrel_port" : 2222,
                // The class were loading, the JDBC Worker
                "worker_class" : "iinteractive.bullfinch.JDBCWorker",
                // The number of JDBC Worker treads to start
                "worker_count" : 2,
                // The queue the workers will monitor
                "subscribe_to" : "test-net-kestrel",
                // The time the workers will spend watching the queue before
                // timing out (then starting to watch again)
                "timeout" : 10000,
                // The amount of time to wait between reconnect attempts
                "retry_time": 20,
                // The number of times to reconnect before giving up
                "retry_attempts": 5
                "options"  : {
                    // JDBC connection information
                    "connection" : {
                        "driver" : "com.mysql.jdbc.Driver",
                        "dsn" : "jdbc:mysql://localhost/labor",
                        "uid" : "root",
                        // the SQL statement that will be used by the DBCP pool to verify the connection
                        "validation" : "SELECT version()"
                    },
                    "statements" : {
                        // A statement with no parameters
                        "getAllAddresses" : {
                            "sql"    : "SELECT * FROM address",
                        },
                        // A statement with parameters
                        "getAllActiveECodesByPage" : {
                            // Note the placeholders for the params
                            "sql"    : "select * from EMT_RENTAL_PRODUCT_V where ISACTIVE = 'N' and ROWNUM >= ? and ROWNUM <= ?",
                            // The types of the parameters
                            "params" : [ "INTEGER", "INTEGER" ]
                        }
                    }
                }
            },
            MORE WORKERS HERE
        ]
    }

This is a lot of options, but they are pretty straightforward. The most
useful for the JDBC worker are the statements.  Optionally, your statements
may have parameters.  Note that you must use placeholders for them!

# HOW IT WORKS *

The JDBC worker waits for messages to enter the queue it is watching.  It
expects the message to look like this:

    { "statement" : "statementName", "params" : [ 12, "foo" ], "response_queue" : "response-blah-blah" }

The params are optional, but required if the statement has parameters!

The ResultSet that comes from the query is then encoded into JSON and returned
one row at a time to the response_queue specified in the message in this form:

    { "row_num": 1, "row_data": { "name": "Steve", "age": 30 } }

## TYPES

The JDBC worker only understands certain types because it's using JSON.  You can use the
following types as params for your queries:

* BOOLEAN
* INTEGER
* NUMBER
* STRING