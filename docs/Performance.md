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
    	// optional: how long to wait between connection retry attempts, defaults to 20
    	"retry_time": 20,
    	// optional: how many times to try and connect, defaults to 5
    	"retry_attempts": 5
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