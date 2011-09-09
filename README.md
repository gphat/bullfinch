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

# LEARNING MORE #

Check out the docs directory.

sunvua
