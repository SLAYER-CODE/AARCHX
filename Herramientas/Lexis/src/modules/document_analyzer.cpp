#include "lexis/modules/document_analyzer.h"

#include <iostream>
#include <algorithm>
#include <sstream>
#include <iomanip>

namespace lexis {

DocumentAnalyzerModule::DocumentAnalyzerModule() {
    // Initialize regex patterns
    date_pattern_ = std::regex(
        R"((?:\d{1,2}[/-]\d{1,2}[/-]\d{2,4})|(?:\d{4}[/-]\d{1,2}[/-]\d{1,2}))",
        std::regex::icase
    );
    
    total_pattern_ = std::regex(
        R"((?:total|amount|sum|balance|due|owed)[:\s]*[$€£¥]?\s*(\d+[.,]\d{2}))",
        std::regex::icase
    );
    
    tax_pattern_ = std::regex(
        R"((?:tax|vat|gst|iva|impuesto)[:\s]*[$€£¥]?\s*(\d+[.,]\d{2}))",
        std::regex::icase
    );
    
    invoice_pattern_ = std::regex(
        R"((?:invoice|bill|receipt|factura|recibo|boleta)[:\s]*#?(\d+))",
        std::regex::icase
    );
    
    phone_pattern_ = std::regex(
        R"((?:\+?\d{1,3}[-.\s]?)?\(?\d{1,4}\)?[-.\s]?\d{1,4}[-.\s]?\d{1,9})",
        std::regex::icase
    );
    
    email_pattern_ = std::regex(
        R"([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,})",
        std::regex::icase
    );
}

DocumentAnalyzerModule::~DocumentAnalyzerModule() {
    shutdown();
}

bool DocumentAnalyzerModule::init() {
    std::cout << "[DocumentAnalyzer] Initialized" << std::endl;
    return true;
}

void DocumentAnalyzerModule::shutdown() {
    std::cout << "[DocumentAnalyzer] Shutdown" << std::endl;
}

void DocumentAnalyzerModule::process_frame(uint32_t* pixels, int w, int h,
                                           FrameResult& result) {
    if (!enabled_ || !result.valid) return;
    
    const auto& blocks = result.document.text_blocks;
    if (blocks.empty()) return;
    
    // Classify document type
    result.document.type = classify_document(blocks);
    
    // Set type label
    switch (result.document.type) {
        case DocType::INVOICE:   result.document.type_label = "Invoice"; break;
        case DocType::RECEIPT:   result.document.type_label = "Receipt"; break;
        case DocType::LABEL:     result.document.type_label = "Product Label"; break;
        case DocType::ID_CARD:   result.document.type_label = "ID Card"; break;
        case DocType::CONTRACT:  result.document.type_label = "Contract"; break;
        case DocType::LETTER:    result.document.type_label = "Letter"; break;
        case DocType::FORM:      result.document.type_label = "Form"; break;
        case DocType::TICKET:    result.document.type_label = "Ticket"; break;
        default:                 result.document.type_label = "Unknown"; break;
    }
    
    // Extract fields based on document type
    result.document.fields = extract_fields(blocks, result.document.type);
    
    // Update classification confidence
    result.document.classification_confidence = 0.8f;  // Placeholder
    
    std::cout << "[DocumentAnalyzer] Document type: " << result.document.type_label 
              << " (" << result.document.fields.size() << " fields extracted)" << std::endl;
}

DocType DocumentAnalyzerModule::classify_document(const std::vector<TextBlock>& blocks) {
    // Calculate scores for each document type
    float invoice_score = classify_invoice(blocks);
    float receipt_score = classify_receipt(blocks);
    float label_score = classify_label(blocks);
    float id_score = classify_id_card(blocks);
    
    // Find highest score
    float max_score = invoice_score;
    DocType best_type = DocType::INVOICE;
    
    if (receipt_score > max_score) {
        max_score = receipt_score;
        best_type = DocType::RECEIPT;
    }
    
    if (label_score > max_score) {
        max_score = label_score;
        best_type = DocType::LABEL;
    }
    
    if (id_score > max_score) {
        max_score = id_score;
        best_type = DocType::ID_CARD;
    }
    
    // If no strong match, return unknown
    if (max_score < 0.3f) {
        return DocType::UNKNOWN;
    }
    
    return best_type;
}

float DocumentAnalyzerModule::classify_invoice(const std::vector<TextBlock>& blocks) {
    float score = 0.0f;
    
    // Check for invoice keywords
    static const std::vector<std::string> keywords = {
        "invoice", "bill", "factura", "invoice number", "bill to", "ship to",
        "payment terms", "due date", "subtotal", "total due"
    };
    
    for (const auto& block : blocks) {
        std::string lower = block.text;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        
        for (const auto& kw : keywords) {
            if (lower.find(kw) != std::string::npos) {
                score += 0.15f;
            }
        }
    }
    
    // Check for invoice number pattern
    if (find_match(blocks, invoice_pattern_).length() > 0) {
        score += 0.2f;
    }
    
    return std::min(score, 1.0f);
}

float DocumentAnalyzerModule::classify_receipt(const std::vector<TextBlock>& blocks) {
    float score = 0.0f;
    
    // Check for receipt keywords
    static const std::vector<std::string> keywords = {
        "receipt", "recibo", "boleta", "thank you", "gracias", "purchase",
        "cashier", "register", "transaction", "change", "subtotal"
    };
    
    for (const auto& block : blocks) {
        std::string lower = block.text;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        
        for (const auto& kw : keywords) {
            if (lower.find(kw) != std::string::npos) {
                score += 0.15f;
            }
        }
    }
    
    // Check for date pattern
    if (find_match(blocks, date_pattern_).length() > 0) {
        score += 0.1f;
    }
    
    return std::min(score, 1.0f);
}

float DocumentAnalyzerModule::classify_label(const std::vector<TextBlock>& blocks) {
    float score = 0.0f;
    
    // Check for label keywords
    static const std::vector<std::string> keywords = {
        "model", "serial", "s/n", "fcc", "ce", "made in", "input",
        "output", "power", "voltage", "current", "capacity"
    };
    
    for (const auto& block : blocks) {
        std::string lower = block.text;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        
        for (const auto& kw : keywords) {
            if (lower.find(kw) != std::string::npos) {
                score += 0.2f;
            }
        }
    }
    
    return std::min(score, 1.0f);
}

float DocumentAnalyzerModule::classify_id_card(const std::vector<TextBlock>& blocks) {
    float score = 0.0f;
    
    // Check for ID card keywords
    static const std::vector<std::string> keywords = {
        "name", "nombre", "address", "dirección", "date of birth",
        "fecha de nacimiento", "id number", "document number", "nationality"
    };
    
    for (const auto& block : blocks) {
        std::string lower = block.text;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        
        for (const auto& kw : keywords) {
            if (lower.find(kw) != std::string::npos) {
                score += 0.2f;
            }
        }
    }
    
    // Check for ID number pattern (long numeric string)
    for (const auto& block : blocks) {
        if (block.text.length() >= 8) {
            bool all_digits = true;
            for (char c : block.text) {
                if (!isdigit(c) && c != '-' && c != ' ') {
                    all_digits = false;
                    break;
                }
            }
            if (all_digits) {
                score += 0.3f;
            }
        }
    }
    
    return std::min(score, 1.0f);
}

std::vector<Field> DocumentAnalyzerModule::extract_fields(const std::vector<TextBlock>& blocks, DocType type) {
    std::vector<Field> fields;
    
    // Always try to extract these common fields
    Field date = extract_date(blocks);
    if (date.confidence > 0.5f) fields.push_back(date);
    
    Field phone = extract_phone(blocks);
    if (phone.confidence > 0.5f) fields.push_back(phone);
    
    Field email = extract_email(blocks);
    if (email.confidence > 0.5f) fields.push_back(email);
    
    // Type-specific extraction
    switch (type) {
        case DocType::INVOICE:
        case DocType::RECEIPT: {
            Field total = extract_total(blocks);
            if (total.confidence > 0.5f) fields.push_back(total);
            
            Field tax = extract_tax(blocks);
            if (tax.confidence > 0.5f) fields.push_back(tax);
            
            Field subtotal = extract_subtotal(blocks);
            if (subtotal.confidence > 0.5f) fields.push_back(subtotal);
            
            Field invoice_num = extract_invoice_number(blocks);
            if (invoice_num.confidence > 0.5f) fields.push_back(invoice_num);
            
            Field vendor = extract_vendor(blocks);
            if (vendor.confidence > 0.5f) fields.push_back(vendor);
            
            Field customer = extract_customer(blocks);
            if (customer.confidence > 0.5f) fields.push_back(customer);
            break;
        }
        
        case DocType::LABEL: {
            Field serial = extract_serial(blocks);
            if (serial.confidence > 0.5f) fields.push_back(serial);
            
            Field model = extract_model(blocks);
            if (model.confidence > 0.5f) fields.push_back(model);
            break;
        }
        
        case DocType::ID_CARD: {
            Field address = extract_address(blocks);
            if (address.confidence > 0.5f) fields.push_back(address);
            break;
        }
        
        default:
            break;
    }
    
    return fields;
}

Field DocumentAnalyzerModule::extract_date(const std::vector<TextBlock>& blocks) {
    Field field;
    field.key = "date";
    field.confidence = 0.0f;
    
    std::string match = find_match(blocks, date_pattern_);
    if (!match.empty()) {
        field.value = match;
        field.confidence = 0.9f;
    }
    
    return field;
}

Field DocumentAnalyzerModule::extract_total(const std::vector<TextBlock>& blocks) {
    Field field;
    field.key = "total";
    field.confidence = 0.0f;
    
    std::smatch m;
    for (const auto& block : blocks) {
        if (std::regex_search(block.text, m, total_pattern_)) {
            field.value = m[1].str();
            field.confidence = 0.85f;
            break;
        }
    }
    
    return field;
}

Field DocumentAnalyzerModule::extract_tax(const std::vector<TextBlock>& blocks) {
    Field field;
    field.key = "tax";
    field.confidence = 0.0f;
    
    std::smatch m;
    for (const auto& block : blocks) {
        if (std::regex_search(block.text, m, tax_pattern_)) {
            field.value = m[1].str();
            field.confidence = 0.85f;
            break;
        }
    }
    
    return field;
}

Field DocumentAnalyzerModule::extract_subtotal(const std::vector<TextBlock>& blocks) {
    Field field;
    field.key = "subtotal";
    field.confidence = 0.0f;
    
    static const std::regex subtotal_re(
        R"((?:subtotal|sub-total|sub total)[:\s]*[$€£¥]?\s*(\d+[.,]\d{2}))",
        std::regex::icase
    );
    
    std::smatch m;
    for (const auto& block : blocks) {
        if (std::regex_search(block.text, m, subtotal_re)) {
            field.value = m[1].str();
            field.confidence = 0.85f;
            break;
        }
    }
    
    return field;
}

Field DocumentAnalyzerModule::extract_invoice_number(const std::vector<TextBlock>& blocks) {
    Field field;
    field.key = "invoice_number";
    field.confidence = 0.0f;
    
    std::string match = find_match(blocks, invoice_pattern_);
    if (!match.empty()) {
        field.value = match;
        field.confidence = 0.9f;
    }
    
    return field;
}

Field DocumentAnalyzerModule::extract_vendor(const std::vector<TextBlock>& blocks) {
    Field field;
    field.key = "vendor";
    field.confidence = 0.0f;
    
    // Look for vendor near top of document (first few lines)
    for (int i = 0; i < std::min(5, (int)blocks.size()); i++) {
        const auto& block = blocks[i];
        if (block.text.length() > 3 && block.text.length() < 50) {
            field.value = block.text;
            field.confidence = 0.7f;
            break;
        }
    }
    
    return field;
}

Field DocumentAnalyzerModule::extract_customer(const std::vector<TextBlock>& blocks) {
    Field field;
    field.key = "customer";
    field.confidence = 0.0f;
    
    // Look for "bill to", "customer", "client" keywords
    static const std::regex customer_re(
        R"((?:bill to|customer|client|cliente|destinatario)[:\s]*(.*))",
        std::regex::icase
    );
    
    std::smatch m;
    for (const auto& block : blocks) {
        if (std::regex_search(block.text, m, customer_re)) {
            field.value = m[1].str();
            field.confidence = 0.8f;
            break;
        }
    }
    
    return field;
}

Field DocumentAnalyzerModule::extract_address(const std::vector<TextBlock>& blocks) {
    Field field;
    field.key = "address";
    field.confidence = 0.0f;
    
    // Look for address keywords
    static const std::regex address_re(
        R"((?:address|dirección|location)[:\s]*(.*))",
        std::regex::icase
    );
    
    std::smatch m;
    for (const auto& block : blocks) {
        if (std::regex_search(block.text, m, address_re)) {
            field.value = m[1].str();
            field.confidence = 0.8f;
            break;
        }
    }
    
    return field;
}

Field DocumentAnalyzerModule::extract_phone(const std::vector<TextBlock>& blocks) {
    Field field;
    field.key = "phone";
    field.confidence = 0.0f;
    
    std::string match = find_match(blocks, phone_pattern_);
    if (!match.empty() && match.length() >= 8) {
        field.value = match;
        field.confidence = 0.85f;
    }
    
    return field;
}

Field DocumentAnalyzerModule::extract_email(const std::vector<TextBlock>& blocks) {
    Field field;
    field.key = "email";
    field.confidence = 0.0f;
    
    std::string match = find_match(blocks, email_pattern_);
    if (!match.empty()) {
        field.value = match;
        field.confidence = 0.95f;
    }
    
    return field;
}

Field DocumentAnalyzerModule::extract_serial(const std::vector<TextBlock>& blocks) {
    Field field;
    field.key = "serial";
    field.confidence = 0.0f;
    
    static const std::regex serial_re(
        R"((?:s/n|serial|serial number|número de serie)[:\s]*([A-Z0-9]{4,}))",
        std::regex::icase
    );
    
    std::smatch m;
    for (const auto& block : blocks) {
        if (std::regex_search(block.text, m, serial_re)) {
            field.value = m[1].str();
            field.confidence = 0.9f;
            break;
        }
    }
    
    return field;
}

Field DocumentAnalyzerModule::extract_model(const std::vector<TextBlock>& blocks) {
    Field field;
    field.key = "model";
    field.confidence = 0.0f;
    
    static const std::regex model_re(
        R"((?:model|型号|型号)[:\s]*([A-Z0-9][\w\-\.]+))",
        std::regex::icase
    );
    
    std::smatch m;
    for (const auto& block : blocks) {
        if (std::regex_search(block.text, m, model_re)) {
            field.value = m[1].str();
            field.confidence = 0.9f;
            break;
        }
    }
    
    return field;
}

std::string DocumentAnalyzerModule::find_match(const std::vector<TextBlock>& blocks, const std::regex& re) {
    for (const auto& block : blocks) {
        std::smatch m;
        if (std::regex_search(block.text, m, re)) {
            return m[0].str();
        }
    }
    return "";
}

std::string DocumentAnalyzerModule::extract_number(const std::string& text) {
    std::string result;
    for (char c : text) {
        if (isdigit(c) || c == '.' || c == ',') {
            result += c;
        }
    }
    return result;
}

} // namespace lexis
