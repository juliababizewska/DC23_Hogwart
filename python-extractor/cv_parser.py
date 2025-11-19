import pymupdf
import morfeusz2
import re
import sys
import os
import json
import docx2txt

# --- Regular Expressions ---

# Matches strict date ranges like "2020 - 2024" or "2020 - obecnie"
REGEX_PERIOD = re.compile(
    r'^\d{4}\s*[-–—]\s*(\d{4}|obecnie|teraz|present)$', 
    re.IGNORECASE
)

# Searches for various date formats (YYYY-YYYY, Month YYYY, DD.MM.YYYY)
REGEX_PERIOD_SEARCH = re.compile(
    # Group 1: YYYY-YYYY or YYYY-present
    r'(\d{4}\s*[-–—]\s*(\d{4}|obecnie|teraz|present))'
    r'|'
    # Group 2: Month YYYY (e.g., Czerwiec 2005)
    r'([A-ZĄĆĘŁŃÓŚŻŹ]+(\s+[A-ZĄĆĘŁŃÓŚŻŹ]+)?\s+\d{4})'
    r'|'
    # Group 3: DD.MM.YYYY - DD.MM.YYYY (or MM.YYYY - MM.YYYY)
    r'((\d{2}\.)?\d{2}\.\d{4}\s*[-–—]\s*(\d{2}\.)?\d{2}\.\d{4})', 
    re.IGNORECASE
)
# Matches dates like "Marzec 2024"
REGEX_MONTH_YEAR_DATE = re.compile(
    r'^[A-ZĄĆĘŁŃÓŚŻŹ]+(\s+[A-ZĄĆĘŁŃÓŚŻŹ]+)?\s+\d{4}$', 
    re.IGNORECASE
)

# Matches date ranges in parentheses, e.g., "(2020 - 2024)"
REGEX_DATE_IN_PARENS = re.compile(
    r'\(\s*\d{4}\s*[-–—]\s*(\d{4}|obecnie|teraz|present)\s*\)$'
)

# Defines the final order of keys in the output JSON
KEY_ORDER = [
    "full_name", "email", "phone", "profile", 
    "education", "experience", "skills", "achievements", 
    "languages", "interests", "footer"
]

# --- File Loading Functions ---

def load_text_from_pdf(pdf_path):
    """
    Extracts text from a PDF file, preserving reading order by sorting text blocks.
    """
    try:
        pdf_document = pymupdf.open(pdf_path)
    except Exception as e:
        print(f"ERROR: Could not open PDF file {pdf_path}. Reason: {e}")
        return None

    full_text = ""
    for page_num in range(pdf_document.page_count):
        page = pdf_document.load_page(page_num)
        
        # Sort blocks by vertical (y) then horizontal (x) position
        blocks = page.get_text("blocks")
        blocks.sort(key=lambda b: (b[1], b[0]))
        
        for b in blocks:
            full_text += b[4] + "\n"  # b[4] is the text content
            
    pdf_document.close()
    return full_text

def load_text_from_docx(docx_path):
    """
    Extracts text from a DOCX file using docx2txt.
    """
    try:
        full_text = docx2txt.process(docx_path)
        return full_text
    except Exception as e:
        print(f"ERROR: Could not open DOCX file {docx_path} with docx2txt. Reason: {e}")
        return None

# --- Morfeusz (Morphological Analyzer) Initialization ---

try:
    # Initialize the Polish morphological analyzer
    morph_analyzer = morfeusz2.Morfeusz()
except Exception as e:
    print(f"ERROR: Could not initialize Morfeusz. {e}")
    sys.exit(1)


# --- Section Segmentation Logic ---

# Keywords (lemmas) used to identify CV sections
SECTION_KEYWORDS = {
    "profile": {"profil", "o", "osobisty", "podsumowanie", "ja", "cel"},
    "experience": {"doświadczenie", "kariera", "praca", "zawodowy", "historia"}, 
    "education": {"wykształcenie", "edukacja", "szkoła", "studia"},
    "skills": {"umiejętność", "skill", "kwalifikacje", "mocne", "strony"},
    "languages": {"język", "obcy"},
    "achievements": {"osiągnięcie", "sukces", "nagrody"},
    "interests": {"zainteresowanie", "hobby"},
}

def get_lemmas_from_line(line_text, morph_instance):
    """
    Analyzes a line of text and returns a set of its lemmas (base forms).
    """
    lemmas = set()
    analysis = morph_instance.analyse(line_text)
    
    for i, word_analysis in enumerate(analysis):
        if word_analysis[2]:  # Check if analysis (lemma, tag) exists
            lemma = word_analysis[2][1].split(":")[0].lower()
            lemmas.add(lemma)
    return lemmas


def segment_cv(full_text, morph_analyzer, keywords):
    """
    Splits the raw CV text into a dictionary of sections (e.g., "experience")
    using keyword lemmas and layout heuristics (like detecting spaced-out text).
    """
    current_section = "header"
    segmented_cv = {
        "header": [],
        "profile": [],
        "experience": [],
        "education": [],
        "skills": [],
        "languages": [],
        "achievements": [],
        "interests": [],
        "footer": []
    }
    
    lines = full_text.split('\n')
    SPACE_TOKEN = "##SEP##" 
    
    for line in lines:
        line_text = line.strip()
        
        if not line_text:
            continue
        
        # --- Heuristic to detect and fix spaced-out text (e.g., "E X P...") ---
        
        num_spaces = sum(1 for c in line_text if c.isspace())
        num_chars = len(line_text) - num_spaces
        
        if num_chars == 0:
            continue
            
        space_ratio = num_spaces / num_chars
        
        # Heuristic: High space ratio + Title/Upper case suggests spaced-out text
        is_potentially_spaced_out = (line_text.isupper() or line_text.istitle())
        is_spaced_out = (space_ratio > 0.8) and is_potentially_spaced_out

        line_for_morph = ""
        line_to_add = ""

        if is_spaced_out:
            # Case 1: Spaced-out text (e.g., "W Y K S Z...")
            # Reconstruct the words by removing single spaces
            line_with_token = re.sub(r'\s{2,}', SPACE_TOKEN, line_text)
            line_despaced = line_with_token.replace(" ", "")
            line_for_morph = line_despaced.replace(SPACE_TOKEN, " ")
            line_to_add = line_for_morph
        
        else:
            # Case 2: Normal text. Just normalize whitespace.
            line_for_morph = re.sub(r'\s+', ' ', line_text)
            line_to_add = line_for_morph
        
        # --- End of heuristic ---

        # Check if the cleaned line is a potential section header
        is_header_candidate = (line_for_morph.isupper() or line_for_morph.istitle()) \
                                    and len(line_for_morph.split()) < 5 

        found_section = None
        if is_header_candidate:
            line_lemmas = get_lemmas_from_line(line_for_morph, morph_analyzer)
            
            for section_name, keyword_lemmas in keywords.items():
                if not line_lemmas.isdisjoint(keyword_lemmas):
                    found_section = section_name
                    break
        
        # Check for GDPR clause
        if "wyrażam zgodę" in line_to_add.lower() or "i agree to the processing" in line_to_add.lower():
            current_section = "footer"
            segmented_cv[current_section].append(line_to_add)
            continue 

        if found_section:
            current_section = found_section
            # Don't add the header itself to the section content
        else:
            # Add the line to the current section
            if current_section not in segmented_cv:
                segmented_cv[current_section] = []
            segmented_cv[current_section].append(line_to_add)
            
    return segmented_cv

# --- Detailed Section Parsers ---

def parse_experience(lines_list, profile_lines, email, phone_raw, junk_regex):
    """
    Parses the 'experience' lines into a structured list of job dictionaries
    (title, period, tasks). Uses heuristics to associate dates with jobs
    and filters out junk data (profile info, contact details).
    """
    jobs_list = []
    current_job = None
    seen_titles = set()
    stray_dates = [] 

    # --- Main parsing loop ---
    for line in lines_list:
        line_cleaned = line.replace("", "").strip()
        line_cleaned = re.sub(r'\s+', ' ', line_cleaned)
        if not line_cleaned:
            continue

        is_period_date = REGEX_PERIOD.match(line_cleaned)
        is_month_year_date = REGEX_MONTH_YEAR_DATE.match(line_cleaned)
        is_date_line = is_period_date or is_month_year_date
        
        is_title_with_date_in_parens = REGEX_DATE_IN_PARENS.search(line_cleaned)
        period_search = REGEX_PERIOD_SEARCH.search(line_cleaned)
        is_title_with_date_inline = period_search and not is_title_with_date_in_parens

        if is_title_with_date_in_parens or is_title_with_date_inline:
            # Case 1: Line contains both title and date
            if current_job: 
                if not current_job.get("title") and current_job.get("tasks"):
                    current_job["title"] = current_job["tasks"].pop(0)
                if current_job.get("title") and current_job["title"] not in seen_titles:
                    jobs_list.append(current_job)
                    seen_titles.add(current_job["title"])
            
            period_match_str = period_search.group(0)
            title = line_cleaned.replace(period_match_str, "").replace("|", " ").strip(" -")
            period = period_match_str.strip("()")
            current_job = {"title": title, "period": period, "tasks": []}

        elif is_date_line:
            # Case 2: Line is only a date
            stray_dates.append(line_cleaned)
        
        else:
            # Case 3: Line is a title, company, or task
            is_new_title_anchor = False
            if (line_cleaned.isupper() and len(line_cleaned.split()) > 1):
                is_new_title_anchor = True
            elif (line_cleaned.istitle() and "," in line_cleaned and 2 <= len(line_cleaned.split()) <= 7):
                is_new_title_anchor = True
            
            if is_new_title_anchor:
                # Case 3a: Likely a new job title
                if current_job: 
                    if not current_job.get("title") and current_job.get("tasks"):
                        current_job["title"] = current_job["tasks"].pop(0)
                    if current_job.get("title") and current_job["title"] not in seen_titles:
                        jobs_list.append(current_job)
                        seen_titles.add(current_job["title"])
                current_job = {"title": line_cleaned, "period": None, "tasks": []}
            elif not current_job:
                # Case 3b: First line, assume it's a title
                current_job = {"title": line_cleaned, "period": None, "tasks": []}
            elif current_job and not current_job.get("title"):
                # Case 3c: Job exists but needs a title
                current_job["title"] = line_cleaned
            elif current_job:
                # Case 3d: Add as a task/description line
                current_job["tasks"].append(line_cleaned)

    # Add the last processed job
    if current_job:
        if not current_job.get("title") and current_job.get("tasks"):
            current_job["title"] = current_job["tasks"].pop(0)
        if current_job.get("title") and current_job["title"] not in seen_titles:
            jobs_list.append(current_job)
            seen_titles.add(current_job["title"])
            
    # --- Post-processing and Filtering ---
            
    # Assign stray dates to jobs without a period
    date_index = 0
    for job in jobs_list:
        if not job.get("period") and date_index < len(stray_dates):
            job["period"] = stray_dates[date_index]
            date_index += 1
            
    final_list = []
    
    # Merge "shell" jobs (e.g., Company name on one line, title/date on the next)
    merged_jobs = []
    i = 0
    while i < len(jobs_list):
        temp_job = jobs_list[i]
        is_shell_job = (not temp_job.get("period")) and (not temp_job.get("tasks"))
        
        if is_shell_job and (i + 1) < len(jobs_list):
            next_job = jobs_list[i + 1]
            temp_job["period"] = next_job.get("period")
            if next_job.get("title"):
                temp_job["tasks"] = [next_job.get("title")] + next_job.get("tasks", [])
            else:
                temp_job["tasks"] = next_job.get("tasks", [])
            merged_jobs.append(temp_job)
            i += 2
        else:
            merged_jobs.append(temp_job)
            i += 1
    
    for job in merged_jobs:
        # Fix cases where start/end dates were on separate lines
        is_title_a_date = REGEX_MONTH_YEAR_DATE.match(str(job.get("title")))
        is_period_a_date = REGEX_MONTH_YEAR_DATE.match(str(job.get("period")))
        
        if is_title_a_date and is_period_a_date and job.get("tasks"):
            new_period = f"{job['period']} - {job['title']}" 
            new_title = job["tasks"].pop(0)
            job["title"] = new_title
            job["period"] = new_period
        
        if job.get("title"):
            cleaned_title = re.sub(r'\s{2,}', ' / ', job["title"])
            cleaned_title = cleaned_title.strip().rstrip('()/ -')
            job["title"] = cleaned_title.strip()

        if job.get("tasks"):
            # Filter 1: Remove profile lines
            job["tasks"] = [t for t in job["tasks"] if t not in profile_lines]
            
            # Filter 2: Remove email/phone
            if email or phone_raw:
                job["tasks"] = [
                    t for t in job["tasks"] 
                    if (not email or email not in t) and 
                       (not phone_raw or phone_raw not in t) and
                       ("telefon:" not in t.lower()) and 
                       ("email:" not in t.lower())
                ]

            # Filter 3: Remove junk regex matches (e.g., "Address:")
            job["tasks"] = [t for t in job["tasks"] if not junk_regex.search(t)]

        # Final check for junk titles
        is_junk_title = job.get("title") in profile_lines
        if is_junk_title and not job.get("period"):
            continue
            
        final_list.append(job)
            
    return final_list

def parse_education(lines_list, profile_lines, email, phone_raw, junk_regex):
    """
    Parses 'education' lines into a structured list of school dictionaries
    (school, period, description). Logic is very similar to parse_experience.
    """
    education_list = []
    current_education = None
    seen_schools = set()
    stray_dates = [] 

    # --- Main parsing loop ---
    for line in lines_list:
        line_cleaned = line.replace("", "").strip()
        line_cleaned = re.sub(r'\s+', ' ', line_cleaned)
        if not line_cleaned:
            continue

        is_period_date = REGEX_PERIOD.match(line_cleaned)
        is_month_year_date = REGEX_MONTH_YEAR_DATE.match(line_cleaned)
        
        # Handle case where start/end dates are on consecutive lines
        if is_month_year_date and \
           current_education and \
           current_education.get("period") and \
           not current_education.get("school") and \
           REGEX_MONTH_YEAR_DATE.match(current_education["period"]):
            
            new_period = f"{line_cleaned} - {current_education['period']}"
            current_education["period"] = new_period
            continue

        is_date_line = is_period_date or is_month_year_date
        
        is_school_with_date_in_parens = REGEX_DATE_IN_PARENS.search(line_cleaned)
        period_search = REGEX_PERIOD_SEARCH.search(line_cleaned)
        is_school_with_date_inline = period_search and not is_school_with_date_in_parens

        if is_school_with_date_in_parens or is_school_with_date_inline:
            # Case 1: Line contains both school and date
            if current_education: 
                if not current_education.get("school") and current_education.get("description"):
                    current_education["school"] = current_education["description"].pop(0)
                if current_education.get("school") and current_education["school"] not in seen_schools:
                    education_list.append(current_education)
                    seen_schools.add(current_education["school"])
            
            period_match_str = period_search.group(0)
            school = line_cleaned.replace(period_match_str, "").replace("|", " ").strip(" -")
            period = period_match_str.strip("()")
            current_education = {"school": school, "period": period, "description": []}

        elif is_date_line:
            # Case 2: Line is only a date
            stray_dates.append(line_cleaned)
        
        else:
            # Case 3: Line is a school, degree, or description
            is_new_title_anchor = False
            if (line_cleaned.isupper() and len(line_cleaned.split()) > 1):
                is_new_title_anchor = True
            elif (line_cleaned.istitle() and "," in line_cleaned and 2 <= len(line_cleaned.split()) <= 7):
                is_new_title_anchor = True

            # Fix for dates that failed the strict regex
            is_stray_date_V5 = REGEX_PERIOD_SEARCH.search(line_cleaned) and not (is_school_with_date_in_parens or is_school_with_date_inline)
            
            if is_stray_date_V5:
                stray_dates.append(line_cleaned)
            elif is_new_title_anchor:
                # Case 3b: Likely a new school/degree
                if current_education: 
                    if not current_education.get("school") and current_education.get("description"):
                        current_education["school"] = current_education["description"].pop(0)
                    if current_education.get("school") and current_education["school"] not in seen_schools:
                        education_list.append(current_education)
                        seen_schools.add(current_education["school"])
                current_education = {"school": line_cleaned, "period": None, "description": []}
            elif not current_education:
                # Case 3c: First line, assume it's a school
                current_education = {"school": line_cleaned, "period": None, "description": []}
            elif current_education and not current_education.get("school"):
                # Case 3d: Education item exists but needs a school/title
                current_education["school"] = line_cleaned
            elif current_education:
                # Case 3e: Add as a description line
                current_education["description"].append(line_cleaned)

    # Add the last processed item
    if current_education:
        if not current_education.get("school") and current_education.get("description"):
            current_education["school"] = current_education["description"].pop(0)
        
        if current_education.get("school") and current_education["school"] not in seen_schools:
            education_list.append(current_education)
            seen_schools.add(current_education["school"])
            
    # --- Post-processing and Filtering ---
            
    # Assign stray dates to items without a period
    date_index = 0
    for edu in education_list:
        if not edu.get("period") and date_index < len(stray_dates):
            edu["period"] = stray_dates[date_index]
            date_index += 1
            
    final_list = []

    # Merge "shell" items (e.g., University on one line, degree/date on the next)
    merged_education = []
    i = 0
    while i < len(education_list):
        temp_edu = education_list[i]
        is_shell_edu = (not temp_edu.get("period")) and (not temp_edu.get("description"))
        
        if is_shell_edu and (i + 1) < len(education_list):
            next_edu = education_list[i + 1]
            temp_edu["period"] = next_edu.get("period")
            if next_edu.get("school"):
                temp_edu["description"] = [next_edu.get("school")] + next_edu.get("description", [])
            else:
                temp_edu["description"] = next_edu.get("description", [])
            merged_education.append(temp_edu)
            i += 2
        else:
            merged_education.append(temp_edu)
            i += 1
            
    for edu in merged_education:
        # Fix cases where start/end dates were on separate lines
        is_school_a_date = REGEX_MONTH_YEAR_DATE.match(str(edu.get("school")))
        is_period_a_date = REGEX_MONTH_YEAR_DATE.match(str(edu.get("period")))
        
        if is_school_a_date and is_period_a_date and edu.get("description"):
            new_period = f"{edu['period']} - {edu['school']}" 
            new_school = edu["description"].pop(0)
            edu["school"] = new_school
            edu["period"] = new_period
        
        if edu.get("school"):
            cleaned_school = re.sub(r'\s{2,}', ' / ', edu["school"])
            cleaned_school = cleaned_school.strip().rstrip('()/ -')
            edu["school"] = cleaned_school.strip()

        if edu.get("description"):
            # Filter 1: Remove profile lines
            edu["description"] = [d for d in edu["description"] if d not in profile_lines]
            
            # Filter 2: Remove email/phone
            if email or phone_raw:
                edu["description"] = [
                    d for d in edu["description"] 
                    if (not email or email not in d) and 
                       (not phone_raw or phone_raw not in d) and
                       ("telefon:" not in d.lower()) and 
                       ("email:" not in d.lower())
                ]
            
            # Filter 3: Remove junk regex matches (e.g., "Address:")
            edu["description"] = [d for d in edu["description"] if not junk_regex.search(d)]

        # Final check for junk titles
        is_junk_school = edu.get("school") in profile_lines
        if is_junk_school and not edu.get("period"):
            continue
            
        final_list.append(edu)
            
    return final_list


# --- Main Data Extraction and Aggregation ---

def extract_details(segmented_cv):
    """
    Orchestrates the entire extraction process.
    1. Finds contact info (email, phone) and name first.
    2. Re-segments the header to find profile/other data.
    3. Cleans and aggregates all sections.
    4. Calls detailed parsers (parse_experience, parse_education).
    5. Formats the final JSON output.
    """
    extracted_data = {}
    
    # STAGE 1: Extract Email and Phone from *ENTIRE* CV
    all_lines = []
    for section_lines in segmented_cv.values():
        all_lines.extend(section_lines)
    full_segmented_text = " ".join(all_lines)
    
    full_name = None
    email = None
    phone = None
    phone_raw = None 

    email_match = re.search(r'[\w\.-]+@[\w\.-]+', full_segmented_text) 
    if email_match:
        email = email_match.group(0).strip()
        extracted_data["email"] = email
        
    phone_match = re.search(r'(\+\d{2})?[ .-]?(?:\d[ .-]?){8,11}\d', full_segmented_text) 
    if not phone_match:
        phone_match = re.search(r'\d{9}', full_segmented_text) 

    if phone_match:
        phone_raw = phone_match.group(0).strip()
        phone = re.sub(r'[^0-9+]', '', phone_raw)
        if len(phone) == 11 and phone.startswith('48'): phone = '+' + phone
        elif len(phone) == 9: phone = '+48' + phone
        extracted_data["phone"] = phone
    
    # STAGE 2: Create "Clean" Header Candidates (remove email/phone)
    header_lines = segmented_cv.get("header", [])
    candidates = []
    for line in header_lines:
        if (email and line == email) or \
           (phone_raw and phone_raw in line) or \
           (phone and phone in line):
            continue
        if line.strip():
            candidates.append(line)

    # STAGE 3: Find Full Name (using heuristics)
    used_indices = set() 

    # 3a: Look for name split into two lines
    if not full_name:
        for i in range(len(candidates) - 1):
            line1 = candidates[i]
            line2 = candidates[i+1]
            is_line1_name = (len(line1.split()) == 1) and line1.istitle() and ":" not in line1
            is_line2_name = (len(line2.split()) == 1) and line2.istitle() and ":" not in line2
            if is_line1_name and is_line2_name:
                full_name = f"{line1} {line2}".title().strip()
                extracted_data["full_name"] = full_name
                used_indices.add(i)
                used_indices.add(i+1)
                break
                
    # 3b: Look for name in a single line
    if not full_name:
        for i, line in enumerate(candidates):
            if i in used_indices: continue
            is_name_like = (line.istitle() or line.isupper()) and \
                           (2 <= len(line.split()) <= 4) and \
                           not line.endswith('.') and \
                           ":" not in line
            if is_name_like:
                full_name = line.title().strip()
                extracted_data["full_name"] = full_name
                used_indices.add(i)
                break

    # 3c: Look for name in a line with a comma (e.g., "Kowalski, Jan")
    if not full_name:
         for i, line in enumerate(candidates):
            if i in used_indices: continue
            if "," in line and (line.istitle() or line.isupper()) and ":" not in line:
                parts = line.split(',')
                name_candidate = parts[0].strip()
                if 1 <= len(name_candidate.split()) <= 2:
                    full_name = name_candidate.title()
                    extracted_data["full_name"] = full_name
                    used_indices.add(i) 
                    break
    
    full_name_local = extracted_data.get("full_name") 
    
    # STAGE 5: Re-segment Header Leftovers & Capture Profile
    
    leftover_header_lines = []
    for i, line in enumerate(candidates):
        if i not in used_indices:
            if full_name_local and line.title() == full_name_local:
                continue
            leftover_header_lines.append(line)
    
    leftover_text = "\n".join(leftover_header_lines)
    
    # Re-run segmentation on the *remaining* header lines
    re_segmented_header = segment_cv(leftover_text, morph_analyzer, SECTION_KEYWORDS)

    explicit_profile = segmented_cv.get('profile', [])
    implicit_profile = re_segmented_header.get('header', [])
    raw_profile = explicit_profile + implicit_profile
    
    deduplicated_profile_list = list(dict.fromkeys(raw_profile))

    final_profile_list = [] 
    # This regex is passed to parsers to filter out junk
    junk_regex = re.compile(r'(\d{2}-\d{3})|(\bul\.)|(@)|(telefon)|(email)|(lokalizacja)|(adres)', re.IGNORECASE)
    
    for line in deduplicated_profile_list:
        if junk_regex.search(line):
            continue
        if phone_raw and phone_raw in line:
            continue
        if REGEX_PERIOD_SEARCH.search(line):
            continue
        
        final_profile_list.append(line)
    
    extracted_data['profile'] = " ".join(final_profile_list)

    # STAGE 6: Process All Other Sections (Merging original + header leftovers)
    
    implicit_leftovers = re_segmented_header.get('header', [])
    
    skills_lines = segmented_cv.get('skills', []) + re_segmented_header.get('skills', [])
    achievements_lines = segmented_cv.get('achievements', []) + re_segmented_header.get('achievements', [])
    footer_lines = segmented_cv.get('footer', []) + re_segmented_header.get('footer', [])
    interests_lines = segmented_cv.get('interests', []) + re_segmented_header.get('interests', [])
    language_lines = segmented_cv.get('languages', []) + re_segmented_header.get('languages', [])
    
    # Add unsegmented leftovers to both Experience and Education for parsing
    experience_lines = segmented_cv.get('experience', []) + re_segmented_header.get('experience', []) + implicit_leftovers
    education_lines = segmented_cv.get('education', []) + re_segmented_header.get('education', []) + implicit_leftovers

    extracted_data['skills'] = list(dict.fromkeys(skills_lines))
    extracted_data['achievements'] = list(dict.fromkeys(achievements_lines))
    extracted_data['footer'] = list(dict.fromkeys(footer_lines))
    extracted_data['interests'] = list(dict.fromkeys(interests_lines)) 

    # Clean language list from page numbers (e.g., "Page 1 / 2")
    processed_languages = []
    page_junk_regex = re.compile(
        r'(page|strona)'
        r'|'
        r'(\d+\s*[/|]\s*\d+)'
        r'|'
        r'(^\d+$)',
        re.IGNORECASE
    )
    
    for line in language_lines: 
        line_stripped = line.strip()
        if "|" in line_stripped:
            parts = [part.strip() for part in line_stripped.split("|")]
            for part in parts:
                if part and not page_junk_regex.search(part): 
                    processed_languages.append(part)
        else:
            if line_stripped and not page_junk_regex.search(line_stripped):
                processed_languages.append(line_stripped)
    
    extracted_data['languages'] = list(dict.fromkeys(processed_languages))

    # Run detailed parsers, passing junk_regex for filtering
    extracted_data['experience'] = parse_experience(experience_lines, final_profile_list, email, phone_raw, junk_regex)
    extracted_data['education'] = parse_education(education_lines, final_profile_list, email, phone_raw, junk_regex)
    
    # STAGE 7: Sort and Clean Final Output
    final_ordered_data = {}
    for key in KEY_ORDER:
        value = extracted_data.get(key)
        if value: # Only add key if it has content
            final_ordered_data[key] = value

    return final_ordered_data

# --- Main Execution ---

if __name__ == "__main__":
    
    CV_DIRECTORY = "./data/cv_files"
    RESULTS_DIRECTORY = "./data/results_json"
    
    os.makedirs(RESULTS_DIRECTORY, exist_ok=True)
    
    print(f"Starting to process files from folder: {CV_DIRECTORY}")
    
    # Iterate through all files in the input directory
    for filename in os.listdir(CV_DIRECTORY):
        input_file_path = os.path.join(CV_DIRECTORY, filename)
        
        if not os.path.isfile(input_file_path):
            continue
            
        print("-" * 40)
        print(f"Processing file: {filename}")
        
        raw_text = None
        
        # Load text based on file extension
        if filename.lower().endswith('.pdf'):
            raw_text = load_text_from_pdf(input_file_path)
        elif filename.lower().endswith('.docx'):
            raw_text = load_text_from_docx(input_file_path)
        else:
            print(f"Skipped file (unsupported extension): {filename}")
            continue
            
        if raw_text is None:
            print(f"Failed to load text from file: {filename}. Skipping.")
            continue
            
        print("...Successfully loaded text...")
        
        # Run segmentation
        print("...Running segmentation parser...")
        segmented_data = segment_cv(raw_text, morph_analyzer, SECTION_KEYWORDS)
        
        # Run detailed extraction
        print("...Extracting detailed data...")
        final_data = extract_details(segmented_data)
        
        # Define output path
        base_filename = os.path.splitext(filename)[0]
        json_filename = base_filename + ".json"
        output_file_path = os.path.join(RESULTS_DIRECTORY, json_filename)
        
        # Save results to JSON
        try:
            with open(output_file_path, 'w', encoding='utf-8') as f:
                json.dump(final_data, f, indent=4, ensure_ascii=False)
            
            print(f"==> Successfully saved result to: {output_file_path}")
            
        except Exception as e:
            print(f"ERROR: Could not save JSON file: {output_file_path}. Reason: {e}")

    print("-" * 40)
    print("All file processing finished.")