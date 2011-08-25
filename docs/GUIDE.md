# NAME

Bullfinch

# DESCRIPTION

Bullfinch is a worker queue that uses kestrel as a medium for storage.
Bullfinch spins up worker threads that monitor and respond to messages in
kestrel queues.

1. Client connects to kestrel and puts a json-formatted message into a queue.
In this message it includes the name of the queue where it will look for a
response.

2. A worker receives the message, does whatever work it needs to do, and
puts one or more items into the queue specified by the client.

3. The client pulls the items out and deletes the queue when it gets the EOF
message.

# NOTE

Bullfinch uses kestrel-client and therefore only speaks Kestrel's
**text protocol**.

# MOTIVATION

We needed a way to bridge the gap between a new system and a legacy one. We
have a future use-case for kestrel and wanted to get it into use.  We also
needed some way to interface with a legacy RDBMS so we wrote Bullinch and the
JDBCWorker to solve that problem.

# HIGH LEVEL OVERVIEW #

Bullfinch works like this:

                                                          +----------+
                                                      /---| Worker A |--+
                                                     /    +-----------  |
                                   +---------------+/     +----------+  |
                                /->| Request Queue |----->| Worker B |--+
    +--------+      +---------+/   +---------------+      +----------+  |
    | Client |----->| Kestrel |                                         /
    +--------+      +---------+<   +----------------+                  /
                                \--| Response Queue |<----------------/
                                   +----------------+

Crystal clear, right?

The Client puts an item into a queue (formatted as json) in the form of:

    {
        "response_queue": "name_of_queue_you_will_read_for_response",
        "other": "stuff",
        "whatever": "you want"
    }

The worker and it's thread(s) are configured to watch a particular queue (see
CONFIGURATION).  Each worker will receive an item from the queue (in some sort
of fair, but random, order) by way of it's `handle` method.  This method will
return an Iterator of strings that will be fed into whatever "response_queue"
was specified in the original request. (Each client should probably use a
unique name, "response_" + pid or something) After your worker returns,
bullfinch will confirm the item you handled.  The client can then draw the
items from the response_queue (whatever it is).  How long it will be before
the item(s) show up there depends on what your worker is doing.  That's your
problem. 

The last record will be followed by an EOF like this:

    { "EOF":"EOF" }

Generally speaking, the best idea is for you to DELETE the response queue
after you encounter the above record.  Since it only exists to satisfy the
request you specified.

# PERFORMANCE

Kestrel's docs contain benchmarks for some of it's load testing, so look there
if you are worried about Kestrel.

Bullfinch isn't really written for raw speed, but it does need to be
reasonably quick. Therefore I present to you this load and burn-in test I did
to verify Bullfinch would work well for us:

Mac Pro running Net::Kestrel clients talking to a Macbook Pro running Windows
7 (bootcamp) with kestrel (configured with an Xmx/Xms of 512M) and bullfinch
(stock VM settings) talking to a MySQL instance back on the Mac Pro.  All
connected over gigabit ethernet.

(This setup is somewhat complex, but it was the easiest way for me to set
things up and the customer uses Windows.)

In this configuration I was able to run 2 clients, sending 10,000 requests
approximately 2 minutes.  This is ~160 req/s.  Each "request" was the
following dance:

1. Client "puts" request into kestrel
2. Worker "gets" request from the queue
3. Worker executes SQL against MySQL
4. Worker encodes ResultSet as JSON and "puts" each row (3 in test case) into the response queue
5. Worker "puts" EOF item into queue
6. Client "gets" each item as they are put into the queue
6. Client "deletes" the queue when it gets the EOF item

So a lot happens in each of the 160 requests per second.  8 queue operations
are performed against kestrel (2,560 ops/s), 160 SQL statements are executed,
480 rows are retrieved and encoded as JSON.

Adding a 3rd client to the mix brought things to a screeching halt.  One client
timed out, but the other two completed very slowly.  Increasing kestrel to it's
default Xmx/Xms of 2048m bought things back in line, completing in 3 minutes.
This told me that ~160req/s was limited by something else.  Perhaps the
latency of talking to MySQL over the network rather than the local box (which
is how we'll do it with the real customer).

# CONFIGURATION

Bullfinch can spin up multiple workers, each with multiple thread instances.
To configure workers, use the following config:

    {
        "workers" : [
            {
                "name" : "JDBC Worker",
                "kestrel_host" : "127.0.0.1",
                "kestrel_port" : 2222,
                "worker_class" : "iinteractive.bullfinch.JDBCWorker",
                "worker_count" : 2,
                "subscribe_to" : "test-net-kestrel",
                "timeout" : 10000,
                "retry_time": 20,
                "retry_attempts": 5
                "options"  : {
                }
            }
        ]
    }

## Required Options ##

### name ###

The name of the worker.  Doesn't have to equate to an actual class or anything
of consequence.  It is totally for logging purposes.

### kestrel_host ###

The host to connect to when talking to kestrel.

### kestrel_port ###

The port to connect to when talking to kestrel.  Defaults to 2222.

### worker_class ###

The fully qualified class name of the class we'll be instantiating for the
worker.

### subscribe_to ###

The name of the kestrel queue from which to draw work items for this worker.

### timeout ###

The timeout that each "get" request will wait for. This prevents bullfinch
from repeatedly banging on kestrel, instead politely waiting for `timeout`
milliseconds to see if anything is ready for work in the queue.

## Optional Options ##

### options ###

This element of the configuration is free form.  Whatever you place here is
assumed to be a HashMap (or whatever the hell your language calls it) and is
passed to the worker when it is configured.

### retry_attempts ###

Bullfinch will try and survive if Kestrel does down or if it otherwise loses
it's network connection.  This setting controls how many times Bullfinch will
attempt to reconnect before giving up and exiting. This helps you survive any
planned or unplanned Kestrel outages.

### retry_time ###

This setting controls how long a worker will sleep before trying to reconnect. If an error occurs then it we will sleep for `retry_time` seconds, then try to reconnect.  If this fails, it will try again, up to `retry_attempts` times.

### worker_count ###

The number of worker instances to create for this worker.  Defaults to 1.

# WORKERS #

To create a worker you must create a class that implements
`iinteractive.bullfinch.Worker` and has implementations of configure and
handle, like so:

    public class FooWorker implements Worker {

        public FooWorker() {

        }

        public void configure(HashMap<String,Object> config) throws Exception {

            // Do something with the config, like config.get("foo");
        }

        public Iterator<String> handle(HashMap<String,Object> request) throws Exception {

            ArrayList<String> foo = new ArrayList<String>();
            foo.add("{ \"foo\" : \"bar\" }");
            return foo.iterator();
        }
    }

## constructor ##

Your constructor must take **no** arguments.

## configure ##

This method will receive the `HashMap<String,Object>` from the `options` key
in the configuration (per-worker).  Any per-instance things (like database
connections or kitchen sinks or whathaveyou) should be setup here.

## handle ##

This method is invoked each time an item from the queue is received and it is
that instance's turn to handle it.  This method must return an
`Iterator<String>`.  This allows you to either create a list or create your own
`Iterator` implementation that actually iterates over something.

# JDBCWorker #

The JDBC worker expects a config like this:

    "options"  : {
        "connection" : {
            "driver" : "com.mysql.jdbc.Driver",
            "dsn" : "jdbc:mysql://localhost/labor",
            "uid" : "root",
            "validation" : "SELECT version()"
        },
        "statements" : {
            "getAllAddresses" : {
                "sql"    : "SELECT * FROM address",
            },
            "getAllActiveECodesByPage" : {
                "sql"    : "select * from EMT_RENTAL_PRODUCT_V where ISACTIVE = 'N' and ROWNUM >= ? and ROWNUM <= ?",
                "params" : [ "INTEGER", "INTEGER" ]
            }
        }
    }

## TYPES

The JDBC worker only understands certain types because it's using JSON.  You can use the
following types as params for your queries:

* BOOLEAN
* INTEGER
* NUMBER
* STRING

Note that Number is a double in Javaland, so use it for non-integer stuff.

## validation

All of these config options are pretty obvious, except for the `validation`
one.  This is the text of a statement used to verify that the connection is
active.  Please replace this with something that makes sense for your RDBMS.

# CONTRIBUTORS

Stevan Little

# AUTHOR

Cory G Watson <cory.watson@iinteractive.com>

# COPYRIGHT AND LICENSE

This software is copyright (c) 2011 by Infinity Interactive, Inc.

This is free software; you can redistribute it and/or modify it under
the same terms as the Perl 5 programming language system itself.
