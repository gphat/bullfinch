# JDBC Query Runner #

The JDBC Query Runner's configuration looks like this:

    {
        "workers" : [
            {
                "name" : "JDBC Worker",
                // The class were loading, the JDBC Worker
                "worker_class" : "iinteractive.bullfinch.minion.JDBCQueryRunner",
                // The number of JDBC Worker treads to start
                "worker_count" : 2,
                "options"  : {
                    // The kestrel host we'll be connecting to
                    "kestrel_host" : "127.0.0.1",
                    // The kestrel port
                    "kestrel_port" : 2222,
                    // The queue the workers will monitor
                    "subscribe_to" : "test-net-kestrel",
                    // The time the workers will spend watching the queue before
                    // timing out (then starting to watch again)
                    "timeout" : 10000,
                    // Requests older than this will be ignored
                    "default_process_by_ttl" : "PT300S",
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

# HOW IT WORKS

The JDBC Query Runner waits for messages to enter the queue it is watching.  It
expects the message to look like this:

    {
        "statement" : "statementName",
        "params" : [ 12, "foo" ],
        "response_queue" : "response-blah-blah",
        "process-by" : "ISO8601 Date" // Optional, so old things can be ignored
    }

The params are optional, but required if the statement has parameters!

The response queue is also optional.  If none is supposed then no response
will be sent back.

The ResultSet that comes from the query is then encoded into JSON and returned
one row at a time to the response_queue specified in the message in this form:

    { "row_num": 1, "row_data": { "name": "Steve", "age": 30 } }

# MULTIPLE STATEMENTS

You may also send multiple statements in one request, like so:

    {
        "statements" : [ "statementName", "otherStatement" ]
        "params" : [ [ 12, "foo" ], [] ],
        "response_queue" : "response-blah-blah",
        "process-by" : "ISO8601 Date" // Optional, so old things can be ignored
    }

Note the use of the keys **statements** rather than **statement**.  Also note
that every statement **must** have a parameter list.  If the statement has
no parameters then it should be empty.

## TYPES

The JDBC worker only understands certain types because it's using JSON.  You can use the
following types as params for your queries:

* BOOLEAN
* INTEGER
* NUMBER
* STRING

## PROCESS-BY

Adding an optional process-by key to the submitted message will cause Bullfinch
to completely ignore the message if the current date is after the one sent. The
dates should be in ISO 8601 format.  This is useful for small requests that
need to be timely.  The client can safely assume that it need not clean up a
response queue, as the message will be dropped.

### default_process_by_ttl

This defaults to PT300.  It uses (Joda-Time)[http://joda-time.sourceforge.net/]
durations. If you do not change this then any request taking over 300s will be
dropped!