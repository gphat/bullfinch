# JDBC Table Scanner #

The JDBC Query Scanner's configuration looks like this:

    {
        "workers" : [
            {
            "name" : "Table Scanner",
            "worker_class" : "iinteractive.bullfinch.minion.JDBCTableScanner",
            "worker_count" : 2,
            "options"  : {
                "kestrel_host" : "127.0.0.1",
                "kestrel_port" : 22133,
                "publish_to" : "test-net-kestrel",
                "interval": 5000,
                "select_query": "SELECT * FROM foobar ORDER BY value ASC LIMIT 10",
                "mark_query": "DELETE FROM foobar WHERE value=?",
                "mark_key": "value",
                "connection" : {
                    "driver" : "com.mysql.jdbc.Driver",
                    "dsn" : "jdbc:mysql://localhost/test",
                    "uid" : "root",
                    "validation" : "SELECT 1"
                }
            },
            MORE WORKERS HERE
        ]
    }

# OPTIONS

## kestrel_host

The hostname/IP of the kestrel instance to connect to

## kestrel_port

The port of the kestrel instance to connect to

## publish_to

The name of the kestrel queue to push rows to

## interval

How often to scan the table in milliseconds.

## select_query

The query to run to "find" rows that you'll be sending.

## delete_query

The query to run to 

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