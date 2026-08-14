#!/bin/bash

# Get the directory this script resides in and make it our current working directory so relative
# file paths work
CWD="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
cd $CWD

sudo -E java -XX:+UseZGC -Xmx64m -Xms64m -jar target/waterapp-1.0.jar 
