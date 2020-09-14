import requests
import json

url = "https://coveralls.io/github/lemurheavy/coveralls-ruby.json"  # Latest build
url = "https://coveralls.io/github/lemurheavy/coveralls-ruby.json?page=1"  # Get 10 builds at a time


def basic_fetching():
    headers = None
    try:
        r = requests.get(url, headers=headers)
    except requests.exceptions.RequestException as e:
        print("e", e)
        return

    if r.status_code != 200:
        print("r.status_code: ", r.status_code)
        return

    try:
        request_dict = r.json()
    except Exception as e:
        print("e", e)
        return

    json_formatted_str = json.dumps(request_dict, indent=4)

    print(json_formatted_str)


if __name__ == '__main__':
    basic_fetching()
