# Changes

## 2.5
* Add manifest bits to ant build (tripside)
* Add testing for invalid JSON input and other minion/worker error
  conditions (gphat)
* Worker subconfigs (tripside)
* JDBC Worker
 * Process-By Limit (tripside)
 * Allow a default_process_by_ttl as a config, defaults to 300
   seconds (tripside)
 * Don't assume that all queries have a resultset, those queries just
   get back an EOF. (gphat)
