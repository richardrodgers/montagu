# Montagu


[![Build Status](https://travis-ci.org/richardrodgers/montagu.svg?branch=master)](https://travis-ci.org/richardrodgers/montagu)

Montagu is a minimal [LDP](https://www.w3.org/TR/ldp/) implementation in the spirit of [Cavendish](https://github.com/cavendish-ldp/cavendish), created to facilitate exploration of
the design space around [Fedora 4](http://duraspace.org/about_fedora) repository use-cases.

Montagu hopes eventually to offer a variety of RDF and binary (Non-RDF) backends,
but the current WIP supports only:

## RDF Stores

A [rdf4j](http://rdf4j.org) (formerly Sesame) triple store (in-memory, or file-backed).
With no configuration, a non-persistent in-memory triple store will be created, but if
the env var RDF_STORE is set, data will be persisted to that location.

## Binary Stores

A [Pairtree](https://tools.ietf.org/html/draft-kunze-pairtree-00) in the local filesystem.
Specify a location with the env var BINARY_STORE, or it will attempt to plant one in the
'binstore' subdirectory of the server's working directory.

An [Amazon S3](https://aws.amazon.com/s3/) object store. Config and credentials vars needed:

    AWS_ACCESS_KEY_ID
    AWS_SECRET_ACCESS_KEY
    AWS_S3_BUCKET_NAME

Montagu will prefer S3 if configured, but fall back to a local pairtree.

## Dependencies

Monatgu uses standard Scala tooling: [SDKMAN!](https://sdkman.io) is your friend.

## Lady Mary Wortley Montagu

    I sometimes give myself admirable advice, but I am incapable of taking it.
