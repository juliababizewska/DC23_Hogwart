import sys, json
import random
import string

def extract_cv(file_path):
    # placeholder candidate data, which could be obtained from the processed file
    name = "Candidate " + ''.join(random.choices(string.digits, k=8))
    mock_data = {
        "name": name,
        "skills": ["magic", "wards"],
        "position": "Teacher",
        "sourceFile": file_path.split('\\')[-1]
    }
    print(json.dumps(mock_data))  # send JSON to stdout

if __name__ == "__main__":
    extract_cv(sys.argv[1])
