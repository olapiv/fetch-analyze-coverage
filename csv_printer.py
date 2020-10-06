import json
import csv
import pandas

outputPath = '/Users/vincent/Desktop/fetch-analyze-coverage/coveralls_importer/temp/outFile.csv'

def print_csv():

    with open(outputPath, 'r') as infile:
        reader = csv.DictReader(infile)
        colnames = reader.fieldnames

    # Previously, for original code:
    # importantColNames = ['repo', 'oldLinesNewlyTested', 'oldLinesNoLongerTested', 'modifiedLinesStillHit', 'modifiedLinesNotHit']

    importantColNames = ['repo', 'oldLinesNewlyHit', 'oldLinesNoLongerHit']

    data = pandas.read_csv(outputPath, names=colnames)
    data = data[importantColNames]

    pandas.set_option('display.max_rows', data.shape[0]+1)
    print(data)


if __name__ == '__main__':
    print_csv()
