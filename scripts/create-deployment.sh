#!/bin/bash

rm -r target/deploy/genomics-qc 2> /dev/null
mkdir -p target/deploy/genomics-qc
mkdir -p target/deploy/genomics-qc/js
cp -r resources/public/css target/deploy/genomics-qc
cp -r resources/public/data target/deploy/genomics-qc
cp -r resources/public/images target/deploy/genomics-qc
cp target/public/cljs-out/prod/main_bundle.js target/deploy/genomics-qc/js/main.js
cp resources/public/index_prod.html target/deploy/genomics-qc/index.html
pushd target/deploy > /dev/null
zip genomics-qc genomics-qc/*
popd > /dev/null
