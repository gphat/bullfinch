# PERFORMANCE TRACKING & TRACING #

If you are using bullfinch to do a lot of work it may be useful to measure
the performance of various actions and to trace individual requests through
the full lifecycle.

## CONFIGURATION

{
    "performance": {
        // Enable performance monitoring
    	"collect": true,
    	// The kestrel host we'll be publishing to
        "kestrel_host" : "172.16.49.130",
        // The kestrel port
        "kestrel_port" : 2222,
        // The queue we'll add to
    	"queue": "metrics",
    	// optional: timeout, how often to send in seconds, defaults to 60
    	"timeout": "60",
    },
    "workers" : [
        …
    ]
}

## HOW IT WORKS

Each worker thread has an instance of a PerformanceCollector object.  If
enabled via the config, this class will accumulate the timing information
given to it by the workers.  Periodically (60 seconds by default) a thread
will wake and empty the timing information by sending it to the queue
specified in the above configuration.

## FORMAT

The timing messages look like this:

    {
        // The name of the performance collector (should be the hostname of the machine that ran it)
        "name": "webserver",
        // The activity that occurred to be timed
        "activity": "Query preparation and execution",
        // ISO 8601 timestamp of when this happened
        "occurred": "2011-09-22T11:32:01Z",
        // Time elapsed, in milliseconds
        "elapsed": "1827"
    }

# TRACING

You can trace a call through bullfinch by adding a "tracer" argument to a
request:

    {
        "statement": "statementName",
        "params": [ 12, "foo" ],
        "response_queue": "response-blah-blah",
        "tracer": "something-unique-like-a-uuid"
    }

Bullfinch will include this tracer in any performance messages as well as
any responses by workers*, such as the JDBC worker.

* Note, this isn't automatic.  Each worker must include the feature.

# COLLECTION

Bullfinch includes an **experimental** feature where the various operations
it performs will log their performance results and emit them into a kestrel
collection for gathering by an outside entity.

This feature can be enabled by providing a performance section in the config
file:

	"performance": {
		"collect": true,
        "kestrel_host" : "172.16.49.130", // the host to connect to
        "kestrel_port" : 2222, // the port
		"queue": "metrics", // the queue to put stuff in
		"timeout": 30, // time to sleep between sends of data
	},

If enabled then Bullfinch will collect information about operations and send
them to the specified queue every $timeout seconds.  The data emitted is JSON
which looks like this:

    {
        "name": "foobar", // the hostname
        "occurred": "2011-12-23T07:33:02Z", // ISO8601 date that the thing happened,
        "activity": "Connection retrieval", // the thing bullfinch did
        "elapsed": 1234, // milliseconds the activity took
        "tracer": "asdasd" // optionally present tracer from request, see TRACING
    }