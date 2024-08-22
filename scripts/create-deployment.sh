#!/bin/bash

rm -r build/genomics-qc 2> /dev/null

mkdir -p build/genomics-qc/js

cp -r resources/public/css build/genomics-qc
cp -r resources/public/data build/genomics-qc
cp -r resources/public/images build/genomics-qc
cp    resources/public/js/main.js build/genomics-qc/js/main.js
cp    resources/public/index.html build/genomics-qc/index.html
cp    resources/public/favicon.ico build/genomics-qc/favicon.ico

pushd build > /dev/null
zip genomics-qc genomics-qc/*
popd > /dev/null
