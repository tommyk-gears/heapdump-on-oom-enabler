# heapdump-on-oom-enabler

A small tool to enable `HeapDumpOnOutOfMemoryError` on a locally running JVM even if it does not 
have remote JMX enabled. This can be useful when you have a running process that you do not want to 
restart, and remote JMX is not enabled.

### Installation:

Just download the single-file program [EnableHeapDumpOnOOM.java](EnableHeapDumpOnOOM.java) to the 
machine where your process runs.

### Usage (assuming Java 11 or later):

`java EnableHeapDumpOnOOM.java <pid>`

One can usually use the `jps` command to find running JVMs and their pid.
