import sys, json
import random
import string

def extract_cv(file_path):
    # placeholder candidate data, which could be obtained from the processed file
    name = "Candidate " + ''.join(random.choices(string.digits, k=8))
    mock_data = {
        "fullname": name,
        "email": "candidate@hogwarts.edu",
        "phone": "+48 555 213 756",
        "profile": "Motivated wizard specializing in defense against the dark arts.",
        "footer": "I solemnly swear that I am up to good work.",
        "position": "Teacher",

        "skills": [
            "Defense Against Dark Arts",
            "Team Leadership",
            "Strategic Thinking",
            "Patronus Casting"
        ],

        "languages": [
            "English",
            "Parseltongue"
        ],

        "education": [
            "Hogwarts School of Witchcraft and Wizardry (1991–1998) - Gryffindor"
        ],

        "achievements": [
            "Triwizard Tournament Champion",
            "Surviving Hogwarts"
        ],

        "interests": [
            "Quidditch",
            "Magical Creatures",
            "Defense Spells"
        ],

        "experience": [
            {
                "title": "Auror Trainee",
                "period": "2020–2023",
                "tasks": [
                    "Assisted senior Aurors in investigations",
                    "Participated in field operations",
                    "Conducted spell safety training"
                ]
            },
            {
                "title": "DA Instructor",
                "period": "2018–2020",
                "tasks": [
                    "Trained new students in defensive magic",
                    "Organized practice duels",
                    "Developed new hex countermeasures"
                ]
            }
        ],
        "sourceFile": file_path.split('\\')[-1]
    }
    print(json.dumps(mock_data))  # send JSON to stdout

if __name__ == "__main__":
    extract_cv(sys.argv[1])
