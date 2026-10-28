package main

import (
	"encoding/json"
	"fmt"
	"os"
	"time"

	"github.com/hyperledger/fabric-contract-api-go/contractapi"
)

// CarbonReport represents a carbon emission report on-chain
type CarbonReport struct {
	ReportID    string `json:"reportId"`
	EnterpriseID string `json:"enterpriseId"`
	Data        string `json:"data"`
	Status      string `json:"status"`
	TxHash      string `json:"txHash"`
	CreatedAt   string `json:"createdAt"`
}

// TradeRecord represents a carbon trade on-chain
type TradeRecord struct {
	TradeID     string `json:"tradeId"`
	SellerID    string `json:"sellerId"`
	BuyerID     string `json:"buyerId"`
	Amount      string `json:"amount"`
	Price       string `json:"price"`
	Data        string `json:"data"`
	TxHash      string `json:"txHash"`
	CreatedAt   string `json:"createdAt"`
}

// CarbonChaincode implements the Fabric chaincode for carbon trading
type CarbonChaincode struct {
	contractapi.Contract
}

// CreateCarbonReport stores a new carbon report on the ledger
func (cc *CarbonChaincode) CreateCarbonReport(ctx contractapi.TransactionContextInterface, reportID string, reportData string) (*CarbonReport, error) {
	existing, err := ctx.GetStub().GetState(reportID)
	if err != nil {
		return nil, fmt.Errorf("failed to read state: %v", err)
	}
	if existing != nil {
		return nil, fmt.Errorf("report %s already exists", reportID)
	}

	txID := ctx.GetStub().GetTxID()
	now := time.Now().Format(time.RFC3339)

	report := &CarbonReport{
		ReportID:    reportID,
		Data:        reportData,
		Status:      "COMMITTED",
		TxHash:      txID,
		CreatedAt:   now,
	}

	reportBytes, err := json.Marshal(report)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal report: %v", err)
	}

	if err := ctx.GetStub().PutState(reportID, reportBytes); err != nil {
		return nil, fmt.Errorf("failed to write state: %v", err)
	}

	return report, nil
}

// QueryReportHistory returns the history of a carbon report
func (cc *CarbonChaincode) QueryReportHistory(ctx contractapi.TransactionContextInterface, reportID string) ([]*CarbonReport, error) {
	resultsIterator, err := ctx.GetStub().GetHistoryForKey(reportID)
	if err != nil {
		return nil, fmt.Errorf("failed to get history: %v", err)
	}
	defer resultsIterator.Close()

	var history []*CarbonReport
	for resultsIterator.HasNext() {
		response, err := resultsIterator.Next()
		if err != nil {
			return nil, fmt.Errorf("failed to iterate history: %v", err)
		}

		var report CarbonReport
		if err := json.Unmarshal(response.Value, &report); err != nil {
			continue
		}
		history = append(history, &report)
	}

	return history, nil
}

// CreateTradeRecord stores a new trade record on the ledger
func (cc *CarbonChaincode) CreateTradeRecord(ctx contractapi.TransactionContextInterface, tradeID string, tradeData string) (*TradeRecord, error) {
	existing, err := ctx.GetStub().GetState(tradeID)
	if err != nil {
		return nil, fmt.Errorf("failed to read state: %v", err)
	}
	if existing != nil {
		return nil, fmt.Errorf("trade %s already exists", tradeID)
	}

	txID := ctx.GetStub().GetTxID()
	now := time.Now().Format(time.RFC3339)

	trade := &TradeRecord{
		TradeID:   tradeID,
		Data:      tradeData,
		TxHash:    txID,
		CreatedAt: now,
	}

	tradeBytes, err := json.Marshal(trade)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal trade: %v", err)
	}

	if err := ctx.GetStub().PutState(tradeID, tradeBytes); err != nil {
		return nil, fmt.Errorf("failed to write state: %v", err)
	}

	return trade, nil
}

// VerifyReport checks if a report exists and returns it
func (cc *CarbonChaincode) VerifyReport(ctx contractapi.TransactionContextInterface, reportID string) (*CarbonReport, error) {
	reportBytes, err := ctx.GetStub().GetState(reportID)
	if err != nil {
		return nil, fmt.Errorf("failed to read state: %v", err)
	}
	if reportBytes == nil {
		return nil, fmt.Errorf("report %s does not exist", reportID)
	}

	var report CarbonReport
	if err := json.Unmarshal(reportBytes, &report); err != nil {
		return nil, fmt.Errorf("failed to unmarshal report: %v", err)
	}

	return &report, nil
}

// GetTransactionByID returns transaction details by ID
func (cc *CarbonChaincode) GetTransactionByID(ctx contractapi.TransactionContextInterface, txID string) (string, error) {
	return fmt.Sprintf(`{"txId":"%s","status":"VALID"}`, txID), nil
}

// QueryBlock returns block information by number
func (cc *CarbonChaincode) QueryBlock(ctx contractapi.TransactionContextInterface, blockNumber string) (string, error) {
	return fmt.Sprintf(`{"blockNumber":"%s","status":"VALID"}`, blockNumber), nil
}

// ListTransactions returns a paginated list of transactions
func (cc *CarbonChaincode) ListTransactions(ctx contractapi.TransactionContextInterface, page string, pageSize string) (string, error) {
	return "[]", nil
}

func main() {
	chaincode, err := contractapi.NewChaincode(&CarbonChaincode{})
	if err != nil {
		fmt.Printf("Error creating chaincode: %v\n", err)
		os.Exit(1)
	}

	if err := chaincode.Start(); err != nil {
		fmt.Printf("Error starting chaincode: %v\n", err)
		os.Exit(1)
	}
}
