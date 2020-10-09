import numpy as np
import matplotlib.pyplot as plt
import csv_printer


category_range = [0, 10, 100, 1000, 10000]
# inspected_variable = 'newLinesHit'
inspected_variable = 'oldLinesNoLongerHit'
# inspected_variable = 'oldLinesNewlyHit'


def format_data():

    data = csv_printer.import_data_pandas()
    repos = data.repo.unique()
    results = {}

    for repo in repos:
        print("----------")
        results[repo] = [0] * (len(category_range) + 1)

        #  Get number of rows for repo (# builds)
        df_repo = data.loc[(data['repo'] == repo)]
        num_builds = df_repo.shape[0]
        print("num_builds: ", num_builds)

        # Calculate less than
        for idx, category in enumerate(category_range):

            if idx == 0:
                df_repo_category = df_repo.loc[(df_repo[inspected_variable] == 0)]
            else:
                df_repo_category = results[repo][idx] = df_repo.loc[(
                    (df_repo[inspected_variable] <= category_range[idx]) &
                    (df_repo[inspected_variable] > category_range[idx-1])
                )]

            print("-----")
            print("category: <=", category)
            num_builds_category = df_repo_category.shape[0]
            print("num_builds_category: ", num_builds_category)

            results[repo][idx] = num_builds_category / num_builds

        # Calculate greater than
        # last_idx = len(category_range) - 1
        # df_repo_category = results[repo][last_idx + 1] = df_repo.loc[(
        #     (df_repo['repo'] == repo) &
        #     (df_repo[inspected_variable] > category_range[last_idx])
        # )]
        # results[repo][last_idx + 1] = df_repo_category[inspected_variable].sum()

    return results

    results = {
        'Repo 1': [10, 15, 17, 32],
        'Repo 2': [26, 22, 29, 10],
        'Repo 3': [35, 37, 7, 2],
        'Repo 4': [32, 11, 9, 15],
        'Repo 5': [21, 29, 5, 5],
        'Repo 6': [8, 19, 5, 30]
    }


def survey(results, category_names):
    """
    Parameters
    ----------
    results : dict
        A mapping from question labels to a list of answers per category.
        It is assumed all lists contain the same number of entries and that
        it matches the length of *category_names*.
    category_names : list of str
        The category labels.
    """
    labels = list(results.keys())
    data = np.array(list(results.values()))
    data_cum = data.cumsum(axis=1)

    # np.linspace(0.15, 0.85, data.shape[1]) --> array([0.15, 0.38333333, 0.61666667, 0.85])

    green = [86, 141, 84, 1]
    yellow = [241, 205, 96, 1]
    orange = [221, 149, 79, 1]
    dark_orange = [206, 93, 63, 1]
    red = [159, 58, 52, 1]
    category_colors = [green, yellow, orange, dark_orange, red]
    for i, color in enumerate(category_colors):
        for ii, number in enumerate(color):
            if ii == 3:
                continue
            category_colors[i][ii] = number / 255

    # category_colors = plt.get_cmap('RdYlGn')(
    # np.linspace(0.05, 0.95, data.shape[1]))
    # category_colors = list(reversed(category_colors))

    print("category_colors: ", category_colors)

    fig, ax = plt.subplots(figsize=(9.2, 5))
    ax.invert_yaxis()
    # ax.xaxis.set_visible(False)
    ax.set_xlabel("Fraction of commits")
    ax.set_xlim(0, np.sum(data, axis=1).max())

    for i, (colname, color) in enumerate(zip(category_names, category_colors)):
        widths = data[:, i]
        starts = data_cum[:, i] - widths
        ax.barh(labels, widths, left=starts, height=0.5,
                label=colname, color=color)
        xcenters = starts + widths / 2

        # Writing numbers into boxes:
        # r, g, b, _ = color
        # text_color = 'white' if r * g * b < 0.5 else 'darkgrey'
        # for y, (x, c) in enumerate(zip(xcenters, widths)):
        #     ax.text(x, y, str(int(c)), ha='center', va='center',
        #             color=text_color)

    ax.legend(ncol=len(category_names), bbox_to_anchor=(0, 1),
              loc='lower left', fontsize='small')

    return fig, ax


if __name__ == '__main__':
    results = format_data()
    survey(results, category_range)
    plt.show()
