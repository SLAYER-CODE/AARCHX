#pragma once

#include "lexis/module.h"

#include <string>
#include <vector>
#include <regex>

namespace lexis {

// ── Document Analyzer Module ─────────────────────────────────────
// Clasifica documentos y extrae campos clave.
// Analiza patrones de texto para identificar tipo de documento
// y extraer información relevante.
class DocumentAnalyzerModule : public AnalysisModule {
public:
    DocumentAnalyzerModule();
    ~DocumentAnalyzerModule();
    
    const char* name() const override { return "document_analyzer"; }
    const char* description() const override { return "Document classification and field extraction"; }
    
    bool init() override;
    void shutdown() override;
    void process_frame(uint32_t* pixels, int w, int h, 
                       FrameResult& result) override;
    
private:
    // Document classification
    DocType classify_document(const std::vector<TextBlock>& blocks);
    float classify_invoice(const std::vector<TextBlock>& blocks);
    float classify_receipt(const std::vector<TextBlock>& blocks);
    float classify_label(const std::vector<TextBlock>& blocks);
    float classify_id_card(const std::vector<TextBlock>& blocks);
    
    // Field extraction
    std::vector<Field> extract_fields(const std::vector<TextBlock>& blocks, DocType type);
    
    // Specific field extractors
    Field extract_date(const std::vector<TextBlock>& blocks);
    Field extract_total(const std::vector<TextBlock>& blocks);
    Field extract_tax(const std::vector<TextBlock>& blocks);
    Field extract_subtotal(const std::vector<TextBlock>& blocks);
    Field extract_invoice_number(const std::vector<TextBlock>& blocks);
    Field extract_vendor(const std::vector<TextBlock>& blocks);
    Field extract_customer(const std::vector<TextBlock>& blocks);
    Field extract_address(const std::vector<TextBlock>& blocks);
    Field extract_phone(const std::vector<TextBlock>& blocks);
    Field extract_email(const std::vector<TextBlock>& blocks);
    Field extract_serial(const std::vector<TextBlock>& blocks);
    Field extract_model(const std::vector<TextBlock>& blocks);
    
    // Helper: find text matching regex
    std::string find_match(const std::vector<TextBlock>& blocks, const std::regex& re);
    
    // Helper: extract number from text
    std::string extract_number(const std::string& text);
    
    // Patterns
    std::regex date_pattern_;
    std::regex total_pattern_;
    std::regex tax_pattern_;
    std::regex invoice_pattern_;
    std::regex phone_pattern_;
    std::regex email_pattern_;
};

} // namespace lexis
