-- Create a schema to logically group related tables
CREATE SCHEMA IF NOT EXISTS pumpschema;

-- Set the search path to the new schema for convenience
SET search_path TO pumpschema;

CREATE TYPE EventSource AS ENUM ('pump','trough-upper','trough-lower');


CREATE TABLE events (
    eventID SERIAL PRIMARY KEY,
    eventSource EventSource NOT NULL,
    eventValue boolean,
    created timestamp default now()
);

CREATE TABLE config (
    param TEXT PRIMARY KEY,
    value TEXT NOT NULL
);