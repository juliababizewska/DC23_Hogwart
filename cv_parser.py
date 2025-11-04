import pymupdf
import morfeusz2
import re
import sys
import os
import json
import docx2txt

# --- Regular Expressions ---

# Matches date ranges like "2020 - 2024" or "2020 - obecnie"
REGEX_PERIOD = re.compile(
    r'^\d{4}\s*[-–—]\s*(\d{4}|obecnie|teraz|present)$', 
    re.IGNORECASE
)
# Matches Polish postal codes like "00-001"
REGEX_POSTAL_CODE = re.compile(r'\d{2}-\d{3}')

# Matches dates like "Marzec 2024"
REGEX_MONTH_YEAR_DATE = re.compile(
    r'^[A-ZĄĆĘŁŃÓŚŻŹ]+(\s+[A-ZĄĆĘŁŃÓŚŻŹ]+)?\s+\d{4}$', 
    re.IGNORECASE
)

# Matches date ranges in parentheses, e.g., "(2020 - 2024)"
REGEX_DATE_IN_PARENS = re.compile(
    r'\(\s*\d{4}\s*[-–—]\s*(\d{4}|obecnie|teraz|present)\s*\)$'
)

# Keywords used to identify potential address lines
ADDRESS_KEYWORDS = {
    "ul.", "ul", "al.", "aleje", "aleja", "os.", "osiedle", "pl.", "plac",
    "street", "str", "avenue", "ave"
}

# Defines the final order of keys in the output JSON
KEY_ORDER = [
    "full_name", "email", "phone", "address", "profile", 
    "education", "experience", "skills", "achievements", 
    "languages", "interests", "footer"
]

# --- File Loading Functions ---

def load_text_from_pdf(pdf_path):
    """
    Extracts text from a PDF file, preserving reading order.
    """
    try:
        pdf_document = pymupdf.open(pdf_path)
    except Exception as e:
        print(f"ERROR: Could not open PDF file {pdf_path}. Reason: {e}")
        return None

    full_text = ""
    for page_num in range(pdf_document.page_count):
        page = pdf_document.load_page(page_num)
        
        # Get text blocks and sort them by vertical (y) then horizontal (x) position
        # This helps maintain the logical reading order of columns.
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
        # docx2txt.process() returns a single formatted string
        # preserving line breaks.
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

# Keywords for identifying CV sections.
# NOTE: Polish lemmas are used for detection with Morfeusz2,
# while the keys (e.g., "profile") are in English for the output.
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
            # Get the lemma (e.g., "pracy" -> "praca")
            lemma = word_analysis[2][1].split(":")[0].lower()
            lemmas.add(lemma)
    return lemmas


def segment_cv(full_text, morph_analyzer, keywords):
    """
    Splits the full CV text into a dictionary of sections based on keywords.
    """
    current_section = "header"  # Default section for contact info
    segmented_cv = {
        "header": [],
        "profile": [],
        "experience": [],
        "education": [],
        "skills": [],
        "languages": [],
        "achievements": [],
        "interests": [],
        "footer": []  # For GDPR/RODO clause
    }
    
    lines = full_text.split('\n')
    
    # A token to temporarily replace multiple spaces.
    # This helps Morfeusz analyze lines that use spacing for layout.
    SPACE_TOKEN = "##SEP##" 
    
    for line in lines:
        line_text = line.strip()
        
        if not line_text:
            continue
            
        # Pre-processing for Morfeusz:
        # 1. Replace 2+ spaces with a token
        line_with_token = re.sub(r'\s{2,}', SPACE_TOKEN, line_text)
        # 2. Remove single spaces (to join words like "K I E R O W N I K")
        line_despaced = line_with_token.replace(" ", "")
        # 3. Restore tokenized spaces for analysis
        line_for_morph = line_despaced.replace(SPACE_TOKEN, " ")
        # 4. Create a clean version to add to the dictionary
        line_to_add = re.sub(r'\s+', ' ', line_text)

        # A line is a header candidate if it's short and in UPPER or Title Case
        is_header_candidate = (line_for_morph.isupper() or line_for_morph.istitle()) \
                                  and len(line_for_morph.split()) < 5 

        found_section = None
        if is_header_candidate:
            line_lemmas = get_lemmas_from_line(line_for_morph, morph_analyzer)
            
            # Check if any lemma matches our section keywords
            for section_name, keyword_lemmas in keywords.items():
                if not line_lemmas.isdisjoint(keyword_lemmas):
                    found_section = section_name
                    break
        
        # Check for GDPR/RODO clause (in Polish or English)
        if "wyrażam zgodę" in line_to_add.lower() or "i agree to the processing" in line_to_add.lower():
            current_section = "footer"
            
        if found_section:
            # If we found a new section, switch to it
            current_section = found_section
        else:
            # Otherwise, add the line to the current section
            if current_section not in segmented_cv:
                segmented_cv[current_section] = []
            segmented_cv[current_section].append(line_to_add)
            
    return segmented_cv

# --- Detailed Section Parsers ---

def parse_experience(lines_list):
    """
    Parses the 'experience' section lines into a list of job dictionaries.
    """
    jobs_list = []
    current_job = None
    
    # A set to track titles and prevent duplicates
    seen_titles = set()

    for line in lines_list:
        is_date_line = REGEX_PERIOD.match(line)
        is_title_with_date_line = REGEX_DATE_IN_PARENS.search(line) 
        # An uppercase line with multiple words is likely a job title or company
        is_title_anchor = line.isupper() and len(line.split()) > 1 

        if is_title_with_date_line:
            # Case 1: Title and date on the same line, e.g., "Manager (2020 - 2022)"
            if current_job: 
                if not current_job["title"] and current_job["tasks"]:
                    current_job["title"] = current_job["tasks"].pop(0)
                
                # Add the previous job if it's not a duplicate
                if current_job["title"] and current_job["title"] not in seen_titles:
                    jobs_list.append(current_job)
                    seen_titles.add(current_job["title"])
            
            # Start a new job entry
            period_match = is_title_with_date_line.group(0)
            title = line.replace(period_match, "").strip(" -")
            period = period_match.strip("()")
            current_job = {"title": title, "period": period, "tasks": []}
        
        elif is_title_anchor: 
            # Case 2: A new uppercase title, likely a new job
            if current_job:
                if not current_job["title"] and current_job["tasks"]:
                    current_job["title"] = current_job["tasks"].pop(0)
                
                if current_job["title"] and current_job["title"] not in seen_titles:
                    jobs_list.append(current_job)
                    seen_titles.add(current_job["title"])
                    
            current_job = {"title": line, "period": None, "tasks": []}
        
        elif is_date_line and not current_job: 
            # Case 3: A date line appears first
            current_job = {"title": None, "period": line, "tasks": []}
        
        elif is_date_line and current_job:
            # Case 4: A date line appears, likely starting a new job
            if current_job["title"] and not current_job["period"]: 
                current_job["period"] = line
            else: 
                if current_job:
                    if not current_job["title"] and current_job["tasks"]:
                        current_job["title"] = current_job["tasks"].pop(0)
                    
                    if current_job["title"] and current_job["title"] not in seen_titles:
                        jobs_list.append(current_job)
                        seen_titles.add(current_job["title"])
                        
                current_job = {"title": None, "period": line, "tasks": []}
        
        elif current_job and not current_job["title"]: 
            # If we have a period but no title, the next line is the title
            current_job["title"] = line

        elif current_job: 
            # Any other line is a task/description
            current_job["tasks"].append(line)
    
    # Add the last job entry after the loop finishes
    if current_job:
        if not current_job["title"] and current_job["tasks"]:
            current_job["title"] = current_job["tasks"].pop(0)
        
        if current_job["title"] and current_job["title"] not in seen_titles:
            jobs_list.append(current_job)
            seen_titles.add(current_job["title"])
            
    return jobs_list

def parse_education(lines_list):
    """
    Parses the 'education' section lines into a list of school dictionaries.
    """
    education_list = []
    current_education = None

    # A set to track school names and prevent duplicates
    seen_schools = set()

    for line in lines_list:
        is_date_line = REGEX_PERIOD.match(line) or REGEX_MONTH_YEAR_DATE.match(line)
        is_school_with_date_line = REGEX_DATE_IN_PARENS.search(line)
        is_school_or_degree = (line.isupper() or line.istitle()) and len(line.split()) > 1

        if is_school_with_date_line:
            # Case 1: School and date on the same line, e.g., "Uniwersytet (2018 - 2021)"
            if current_education: 
                if not current_education["school"] and current_education["description"]:
                    current_education["school"] = current_education["description"].pop(0)
                
                # Add previous entry if not a duplicate
                if current_education["school"] and current_education["school"] not in seen_schools:
                    education_list.append(current_education)
                    seen_schools.add(current_education["school"])
            
            # Start a new education entry
            period_match = is_school_with_date_line.group(0)
            school = line.replace(period_match, "").strip(" -")
            period = period_match.strip("()")
            current_education = {"school": school, "period": period, "description": []}
        
        elif is_date_line:
            # Case 2: A new date line, likely a new entry
            if current_education:
                if not current_education["school"] and current_education["description"]:
                    current_education["school"] = current_education["description"].pop(0)
                
                if current_education["school"] and current_education["school"] not in seen_schools:
                    education_list.append(current_education)
                    seen_schools.add(current_education["school"])
            
            current_education = {"school": None, "period": line, "description": []}
        
        elif current_education and not current_education["school"] and is_school_or_degree:
            # If we have a period but no school, the next title-case line is the school
            current_education["school"] = line
            
        elif current_education:
            # Any other line is a description (e.g., degree, specialization)
            current_education["description"].append(line)

    # Add the last education entry
    if current_education:
        if not current_education["school"] and current_education["description"]:
            current_education["school"] = current_education["description"].pop(0)
        
        if current_education["school"] and current_education["school"] not in seen_schools:
            education_list.append(current_education)
            seen_schools.add(current_education["school"])
            
    return education_list

# --- Main Data Extraction and Aggregation ---

def extract_details(segmented_cv):
    """
    Aggregates all segmented data, parses the header, and structures the final output.
    """
    extracted_data = {}
    
    # --- STAGE 1: Extract Email and Phone from Header ---
    header_lines = segmented_cv.get("header", [])
    header_text = " ".join(header_lines)
    
    full_name = None
    email = None
    phone = None
    address = None 
    phone_raw = None # To store the original phone string for filtering

    email_match = re.search(r'[\w\.-]+@[\w\.-]+', header_text)
    if email_match:
        email = email_match.group(0).strip()
        extracted_data["email"] = email
        
    # Regex for various phone formats (e.g., +48 123 456 789, 123-456-789)
    phone_match = re.search(r'(\+\d{2})?[ .-]?(?:\d[ .-]?){8,11}\d', header_text)
    if not phone_match:
        phone_match = re.search(r'\d{9}', header_text) # Fallback for 9-digit numbers

    if phone_match:
        phone_raw = phone_match.group(0).strip()
        # Normalize phone to +48 format
        phone = re.sub(r'[^0-9+]', '', phone_raw)
        if len(phone) == 11 and phone.startswith('48'): phone = '+' + phone
        elif len(phone) == 9: phone = '+48' + phone
        extracted_data["phone"] = phone
    
    # --- STAGE 2: Create "Clean" Header Candidates ---
    # These are lines from the header that are NOT email or phone.
    candidates = []
    for line in header_lines:
        if (email and line == email) or \
           (phone_raw and phone_raw in line) or \
           (phone and phone in line):
            continue
        if line.strip():
            candidates.append(line)

    # --- STAGE 3: Find Full Name (using heuristics) ---
    used_indices = set() # To track which candidate lines we've used

    # 3a: Look for name split into two lines (e.g., "Jan" \n "Kowalski")
    if not full_name:
        for i in range(len(candidates) - 1):
            line1 = candidates[i]
            line2 = candidates[i+1]
            # Check if both are single, title-cased words (and not a postal code)
            is_line1_name = (len(line1.split()) == 1) and line1.istitle() and not REGEX_POSTAL_CODE.search(line1)
            is_line2_name = (len(line2.split()) == 1) and line2.istitle() and not REGEX_POSTAL_CODE.search(line2)
            if is_line1_name and is_line2_name:
                full_name = f"{line1} {line2}".title().strip()
                extracted_data["full_name"] = full_name
                used_indices.add(i)
                used_indices.add(i+1)
                break
                
    # 3b: Look for name in a single line (e.g., "Jan Kowalski")
    if not full_name:
        for i, line in enumerate(candidates):
            if i in used_indices: continue
            # Check for 2-4 words, Title/UPPER case, not ending in a period
            is_name_like = (line.istitle() or line.isupper()) and \
                             (2 <= len(line.split()) <= 4) and \
                             not line.endswith('.')
            if is_name_like and not REGEX_POSTAL_CODE.search(line):
                full_name = line.title().strip()
                extracted_data["full_name"] = full_name
                used_indices.add(i)
                break

    # 3c: Look for name in a line with a comma (e.g., "Kowalski, Jan")
    if not full_name:
         for i, line in enumerate(candidates):
            if i in used_indices: continue
            if "," in line and (line.istitle() or line.isupper()):
                parts = line.split(',')
                name_candidate = parts[0].strip()
                if 1 <= len(name_candidate.split()) <= 2:
                    full_name = name_candidate.title()
                    extracted_data["full_name"] = full_name
                    used_indices.add(i) 
                    break
    
    full_name_local = extracted_data.get("full_name") 

    # --- STAGE 4: Find Address (using heuristics) ---
    for i, line in enumerate(candidates):
        if i in used_indices and not "," in line:
             continue
        
        # Validation: Skip lines that are too long or look like sentences
        if len(line) > 100 or line.endswith('.'):
            continue
            
        line_lower = line.lower()
        is_address_like = False
        
        # Heuristic 1: Contains postal code or address keyword
        if REGEX_POSTAL_CODE.search(line) or \
           any(keyword in line_lower for keyword in ADDRESS_KEYWORDS):
            is_address_like = True
        # Heuristic 2: Contains a comma (e.g., "Warszawa, Polska")
        elif "," in line and len(line.split()) < 8:
            if full_name_local and line.title().startswith(full_name_local):
                 parts = line.split(',')
                 if len(parts) > 1:
                    address = parts[1].strip()
                    is_address_like = True
                 else:
                    continue
            else:
                is_address_like = True
        # Heuristic 3: Short, title-cased line (e.g., "Warszawa")
        elif line.istitle() and len(line.split()) <= 5:
             if not line.endswith('.') and not len(line.split()) > 5:
                is_address_like = True

        if is_address_like:
            if not address: 
                address = line.strip()
            # Clean up address string if it contains name, email, or phone
            if full_name_local and address.title().startswith(full_name_local):
                address = address.title().replace(full_name_local, "").strip(" ,")
            if email and email in address:
                address = address.replace(email, "").strip(" •-")
            if phone_raw and phone_raw in address:
                address = address.replace(phone_raw, "").strip(" •-")

            extracted_data["address"] = address.strip()
            used_indices.add(i)
            break

    # --- STAGE 5: Capture Profile ---
    # Combine the dedicated 'profile' section with any unused lines from the header
    profile_lines = []
    for i, line in enumerate(candidates):
        if i not in used_indices:
            # Don't add the name to the profile if we already found it
            if full_name_local and line.title() == full_name_local:
                continue
            profile_lines.append(line)
    
    # Combine and deduplicate
    raw_profile = segmented_cv.get('profile', []) + profile_lines
    # Use dict.fromkeys() for fast, order-preserving deduplication
    extracted_data['profile'] = list(dict.fromkeys(raw_profile)) 

    # --- STAGE 6: Process All Other Sections with Deduplication ---
    
    extracted_data['skills'] = list(dict.fromkeys(segmented_cv.get('skills', [])))
    extracted_data['achievements'] = list(dict.fromkeys(segmented_cv.get('achievements', [])))
    extracted_data['footer'] = list(dict.fromkeys(segmented_cv.get('footer', [])))
    extracted_data['interests'] = list(dict.fromkeys(segmented_cv.get('interests', []))) 

    # Special handling for languages: split lines containing "|"
    raw_language_lines = segmented_cv.get('languages', [])
    processed_languages = []
    for line in raw_language_lines:
        line_stripped = line.strip()
        if "|" in line_stripped:
            parts = [part.strip() for part in line_stripped.split("|")]
            for part in parts:
                if len(part) >= 5: # Filter out noise
                    processed_languages.append(part)
        else:
            if len(line_stripped) >= 5: # Filter out noise
                processed_languages.append(line_stripped)
    
    # Deduplicate at the end
    extracted_data['languages'] = list(dict.fromkeys(processed_languages))

    # Parse the structured sections
    extracted_data['experience'] = parse_experience(
        segmented_cv.get('experience', [])
    )
    extracted_data['education'] = parse_education(
        segmented_cv.get('education', [])
    )
    
    # --- STAGE 7: Sort and Clean Final Output ---
    final_ordered_data = {}
    for key in KEY_ORDER:
        value = extracted_data.get(key)
        # Only add keys that have content
        if value: 
            final_ordered_data[key] = value

    return final_ordered_data

# --- Main Execution ---

if __name__ == "__main__":
    
    CV_DIRECTORY = "cv" 
    RESULTS_DIRECTORY = "wyniki_json"
    
    # Create the results directory if it doesn't exist
    os.makedirs(RESULTS_DIRECTORY, exist_ok=True)
    
    print(f"Starting to process files from folder: {CV_DIRECTORY}")
    
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
        
        print("...Running segmentation parser...")
        segmented_data = segment_cv(raw_text, morph_analyzer, SECTION_KEYWORDS)
        
        print("...Extracting detailed data...")
        final_data = extract_details(segmented_data)
        
        # Prepare output JSON file path
        base_filename = os.path.splitext(filename)[0]
        json_filename = base_filename + ".json"
        output_file_path = os.path.join(RESULTS_DIRECTORY, json_filename)
        
        try:
            # Save the structured data as a JSON file
            with open(output_file_path, 'w', encoding='utf-8') as f:
                json.dump(final_data, f, indent=4, ensure_ascii=False)
            
            print(f"==> Successfully saved result to: {output_file_path}")
            
        except Exception as e:
            print(f"ERROR: Could not save JSON file: {output_file_path}. Reason: {e}")

    print("-" * 40)
    print("All file processing finished.")