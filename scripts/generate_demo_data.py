#!/usr/bin/env python3

import argparse
import csv
import datetime
import json
import os
import random


def parse_sequencers_list(sequencers_list_file):
    """
    """
    sequencers = []
    with open(sequencers_list_file, 'r') as f:
        reader = csv.DictReader(f)
        for row in reader:
            row['latest_run_num'] = int(row['latest_run_num'])
            sequencers.append(row)

    return sequencers


def generate_random_char():
    """
    """
    next_char = None
    number = random.randint(0, 1) > 0.5
    if number:
        next_char = str(random.randint(0, 9))
    else:
        next_char = random.choice('ABCDEFGHIJKLMNOPQRSTUVWXYZ')

    return next_char
    

def generate_random_flowcell_id(sequencer_type):
    """
    """
    flowcell_id = None
    if sequencer_type == 'miseq':
        flowcell_id_zeros = "000000000-"
        flowcell_id = flowcell_id_zeros
        for i in range(0, 5):
            next_char = generate_random_char()
            flowcell_id += next_char
    if sequencer_type == 'nextseq':
        flowcell_id = ""
        for i in range(0, 9):
            next_char = generate_random_char()
            flowcell_id += next_char

    return flowcell_id


def generate_run_id(sequencer, run_date):
    """
    """
    two_digit_year = run_date.strftime('%y')
    two_digit_month = run_date.strftime('%m')
    two_digit_day = run_date.strftime('%d')
    run_date_str = f"{two_digit_year}{two_digit_month}{two_digit_day}"
    sequencer['latest_run_date'] = datetime.datetime.strftime(run_date, '%Y-%m-%d')
    instrument_id = sequencer['instrument_id']
    run_num = sequencer['latest_run_num'] + 1
    run_num_formatted = None

    if sequencer['instrument_type'] == 'miseq':
        run_num_formatted = f"{run_num:03d}"
    if sequencer['instrument_type'] == 'nextseq':
        run_num_formatted = f"{run_num}"

    flowcell_id = generate_random_flowcell_id(sequencer['instrument_type'])

    run_id = "_".join([
        run_date_str,
        instrument_id,
        run_num_formatted,
        flowcell_id,
    ])

    return run_id

def parse_projects(projects_file, species_file, projects_species_file):
    """
    """
    projects_by_id = {}
    with open(projects_file, 'r') as f:
        reader = csv.DictReader(f)
        for row in reader:
            projects_by_id[row['project_id']] = row

    species_by_id = {}
    with open(species_file, 'r') as f:
        reader = csv.DictReader(f)
        for row in reader:
            row['genome_size_mb'] = float(row['genome_size_mb'])
            species_by_id[row['ncbi_taxonomy_id']] = row

    projects_species = []
    with open(projects_species_file, 'r') as f:
        reader = csv.DictReader(f)
        for row in reader:
            projects_species.append(row)

    for project_id, project in projects_by_id.items():
        project['species'] = []
        for project_species in projects_species:
            if project_species['project_id'] == project_id:
                species_id = project_species['ncbi_taxonomy_id']
                project['species'].append(species_by_id[species_id])

    return projects_by_id


def generate_libraries(projects, instrument_type, last_library_num=1):
    """
    """
    num_libs_to_generate = 96
    if instrument_type == 'miseq':
        num_libs_to_generate = 96
    if instrument_type == 'nextseq':
        num_libs_to_generate = 384

    libraries = []
    run_mean_q30 = random.randint(80, 100)
    for i in range(0, num_libs_to_generate):
        last_library_num += 1
        library_id = f"S{last_library_num:05d}"
        project_id = random.choice(list(projects.keys()))
        species = random.choice(projects[project_id]['species'])
        genome_size_mb = species['genome_size_mb']
        if species['genome_size_mb'] > 1:
            total_bases = random.randint(250_000_000, 750_000_000)
        else:
            total_bases = random.randint(1_000_000, 5_000_000)
        percent_bases_above_q30 = round(random.gauss(run_mean_q30, 1), 2)
        estimated_depth = round(total_bases / (genome_size_mb * 1000000), 2)
        library = {
            'library_id': library_id,
            'project_id': project_id,
            'inferred_species_name': species['species_name'],
            'inferred_species_genome_size_mb': species['genome_size_mb'],
            'total_bases': total_bases,
            'percent_bases_above_q30': percent_bases_above_q30,
            'estimated_depth': estimated_depth,
            
        }
        libraries.append(library)

    return libraries, last_library_num


def main(args):
    start_date = datetime.datetime.strptime(args.start_date, '%Y-%m-%d')
    sequencers = parse_sequencers_list(args.sequencers)
    latest_library_num = args.start_library_num

    os.makedirs(os.path.join(args.outdir, 'library-qc'), exist_ok=True)

    for sequencer in sequencers:
        sequencer['latest_run_date'] = start_date.strftime('%Y-%m-%d')
        sequencer['runs'] = []

    for i in range(0, args.num):
        chosen_sequencer = random.choice(sequencers)
        num_days_since_last_run = random.randint(1, 7)
        run_date = datetime.datetime.strptime(chosen_sequencer['latest_run_date'], '%Y-%m-%d') + datetime.timedelta(days=num_days_since_last_run)
        run_id = generate_run_id(chosen_sequencer, run_date)
        chosen_sequencer['runs'].append({
            'sequencing_run_id': run_id,
            'sequencing_run_date': run_date.strftime('%Y-%m-%d'),
        })
        sequencer['latest_run_num'] += 1

    sequencing_runs = []
    for sequencer in sequencers:
        sequencing_runs.extend(sequencer['runs'])

    with open(os.path.join(args.outdir, 'sequencing-runs.json'), 'w') as f:
        json.dump(sequencing_runs, f, indent=4)
        f.write('\n')

    projects = parse_projects(args.projects, args.species, args.projects_species)

    for sequencer in sequencers:
        instrument_id = sequencer['instrument_id']
        instrument_type = sequencer['instrument_type']
        for run in sequencer['runs']:
            run_id = run['sequencing_run_id']
            libraries, latest_library_num = generate_libraries(projects, instrument_type, last_library_num=latest_library_num)
            run['libraries'] = libraries

    for sequencer in sequencers:
        for run in sequencer['runs']:
            with open(os.path.join(args.outdir, 'library-qc', f"{run['sequencing_run_id']}-library-qc.json"), 'w') as f:
                json.dump(run['libraries'], f, indent=4)
                f.write('\n')

    


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Generate demo data')
    parser.add_argument('-o', '--outdir', type=str, default='.', help='output directory')
    parser.add_argument('-n', '--num', type=int, default=10, help='number of runs')
    parser.add_argument('-s', '--sequencers', type=str, help='sequencers csv file')
    parser.add_argument('--projects', type=str, help='projects csv file')
    parser.add_argument('--species', type=str, help='species csv file')
    parser.add_argument('--projects-species', type=str, help='projects-species csv file')
    parser.add_argument('-d', '--start-date', type=str, default="2023-01-01", help='start date for the first run, must be in the format YYYY-MM-DD')
    parser.add_argument('--start-library-num', type=int, default=384, help='start library number')
    args = parser.parse_args()
    main(args)
