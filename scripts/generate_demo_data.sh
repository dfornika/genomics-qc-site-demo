#!/bin/bash


outdir=resources/public/data
mkdir -p ${outdir}

./scripts/generate_demo_data.py \
    --sequencers .github/data/sequencing_instruments.csv \
    --projects .github/data/projects.csv \
    --species .github/data/species.csv \
    --projects-species .github/data/project_species.csv \
    --start-date 2023-01-01 \
    --start-library-num 1 \
    --num 100 \
    --outdir ${outdir}
