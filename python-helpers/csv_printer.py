import json
import csv
import pandas


outputPath = '/Users/vincent/Desktop/fetch-analyze-coverage/coveralls_importer/temp/outFile.csv'
importantColNames = ['repo', 'newLinesHit', 'oldLinesNoLongerHit']
non_numeric_cols = ["repo", "childSha", "parentSha", "childBranch"]

# For original code:
# importantColNames = ['repo', 'oldLinesNewlyTested', 'oldLinesNoLongerTested', 'modifiedLinesStillHit', 'modifiedLinesNotHit']


def import_data_pandas():
    with open(outputPath, 'r') as infile:
        reader = csv.DictReader(infile)
        colnames = reader.fieldnames

    data = pandas.read_csv(outputPath, header=1, names=colnames)
    numeric_cols = list(set(colnames) - set(non_numeric_cols))
    data[numeric_cols] = data[numeric_cols].apply(
        pandas.to_numeric, errors='coerce')
    print(data[importantColNames])
    return data


def print_metadata():
    data = import_data_pandas()

    data = data.groupby(["repo"]).size().reset_index(name='builds analyzed')
    data.to_excel("./builds_per_repo.xlsx")
    print(data)


def print_csv():
    data = import_data_pandas()
    data = data[importantColNames]
    pandas.set_option('display.max_rows', data.shape[0]+1)
    data.to_excel("./output.xlsx")
    print(data)


if __name__ == '__main__':
    print_metadata()
