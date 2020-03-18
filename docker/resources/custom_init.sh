#!/bin/bash

# Put custom comands here and override it on Docker run

./wait-for-it.sh db:3306 -- echo "MySQL is up"