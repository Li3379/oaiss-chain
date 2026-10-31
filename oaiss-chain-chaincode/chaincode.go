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
	ReportID     string `json:"reportId"`
	EnterpriseID string `json:"enterpriseId"`
	Data         string `json:"data"`
	Status       string `json:"status"`
	TxHash       string `json:"txHash"`
	CreatedAt    string `json:"createdAt"`
}

// TradeRecord represents a carbon trade on-chain
type TradeRecord struct {
	TradeID   string `json:"tradeId"`
	SellerID  string `json:"sellerId"`
	BuyerID   string `json:"buyerId"`
	Amount    string `json:"amount"`
	Price     string `json:"price"`
	Data      string `json:"data"`
	TxHash    string `json:"txHash"`
	CreatedAt string `json:"createdAt"`
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
		ReportID:  reportID,
		Data:      reportData,
		Status:    "COMMITTED",
		TxHash:    txID,
		CreatedAt: now,
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

// CarbonReportSnapshot 是碳报告的完整链上快照（v2）。
//
// 与 v1 的区别：v1 只存 Data（emissionData 原文），EnterpriseID 字段从未赋值，
// 链上无法回答「这份报告属于哪家企业」——存证因此无法独立验证主体归属。
//
// v2 补齐主体、周期、总量、三范围与签名，使链上记录可以独立回答：
// 「谁、什么时候、报了多少、内容是否被改过」。
type CarbonReportSnapshot struct {
	ReportID         string `json:"reportId"`
	EnterpriseID     string `json:"enterpriseId"`
	AccountingPeriod string `json:"accountingPeriod"`
	TotalEmission    string `json:"totalEmission"`
	Scope1Emission   string `json:"scope1Emission"`
	Scope2Emission   string `json:"scope2Emission"`
	Scope3Emission   string `json:"scope3Emission"`
	Signature        string `json:"signature"`
	Data             string `json:"data"`
	Status           string `json:"status"`
	TxHash           string `json:"txHash"`
	CreatedAt        string `json:"createdAt"`
	SchemaVersion    string `json:"schemaVersion"`
}

// CreateCarbonReportV2 存证完整报告快照（推荐路径）。
//
// 与 v1 CreateCarbonReport 并存，不破坏已有调用方与历史链上记录。
// 新记录带 schemaVersion="v2"，查询时可与新老记录区分。
func (cc *CarbonChaincode) CreateCarbonReportV2(
	ctx contractapi.TransactionContextInterface,
	reportID string,
	enterpriseID string,
	accountingPeriod string,
	totalEmission string,
	scope1Emission string,
	scope2Emission string,
	scope3Emission string,
	signature string,
	reportData string,
) (*CarbonReportSnapshot, error) {
	existing, err := ctx.GetStub().GetState(reportID)
	if err != nil {
		return nil, fmt.Errorf("failed to read state: %v", err)
	}
	if existing != nil {
		return nil, fmt.Errorf("report %s already exists", reportID)
	}

	txID := ctx.GetStub().GetTxID()
	now := time.Now().Format(time.RFC3339)

	snapshot := &CarbonReportSnapshot{
		ReportID:         reportID,
		EnterpriseID:     enterpriseID,
		AccountingPeriod: accountingPeriod,
		TotalEmission:    totalEmission,
		Scope1Emission:   scope1Emission,
		Scope2Emission:   scope2Emission,
		Scope3Emission:   scope3Emission,
		Signature:        signature,
		Data:             reportData,
		Status:           "COMMITTED",
		TxHash:           txID,
		CreatedAt:        now,
		SchemaVersion:    "v2",
	}

	snapshotBytes, err := json.Marshal(snapshot)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal snapshot: %v", err)
	}

	if err := ctx.GetStub().PutState(reportID, snapshotBytes); err != nil {
		return nil, fmt.Errorf("failed to write state: %v", err)
	}

	// 建立「企业 → 报告」索引，使链上可按企业查询（v1 缺失此能力）
	indexKey, err := ctx.GetStub().CreateCompositeKey("enterprise~report", []string{enterpriseID, reportID})
	if err == nil {
		_ = ctx.GetStub().PutState(indexKey, []byte{0x00})
	}

	return snapshot, nil
}

// QueryReportsByEnterprise 按企业查询其所有链上报告（v2 索引）。
func (cc *CarbonChaincode) QueryReportsByEnterprise(
	ctx contractapi.TransactionContextInterface,
	enterpriseID string,
) ([]*CarbonReportSnapshot, error) {
	resultsIterator, err := ctx.GetStub().GetStateByPartialCompositeKey(
		"enterprise~report", []string{enterpriseID})
	if err != nil {
		return nil, fmt.Errorf("failed to query by enterprise: %v", err)
	}
	defer resultsIterator.Close()

	var snapshots []*CarbonReportSnapshot
	for resultsIterator.HasNext() {
		item, err := resultsIterator.Next()
		if err != nil {
			return nil, err
		}
		_, parts, err := ctx.GetStub().SplitCompositeKey(item.Key)
		if err != nil || len(parts) < 2 {
			continue
		}
		state, err := ctx.GetStub().GetState(parts[1])
		if err != nil || state == nil {
			continue
		}
		var snapshot CarbonReportSnapshot
		if err := json.Unmarshal(state, &snapshot); err != nil {
			continue
		}
		snapshots = append(snapshots, &snapshot)
	}
	return snapshots, nil
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
