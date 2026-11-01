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
//
// 过程排放（脱硫 / 线损 / SF6）单独成字段：`totalEmission` 含这三项，
// 若只存总量与三范围，链上核对时必然出现「总量 ≠ 三范围之和」而无法解释。
type CarbonReportSnapshot struct {
	ReportID          string `json:"reportId"`
	ReportNo          string `json:"reportNo"`
	EnterpriseID      string `json:"enterpriseId"`
	AccountingPeriod  string `json:"accountingPeriod"`
	TotalEmission     string `json:"totalEmission"`
	Scope1Emission    string `json:"scope1Emission"`
	Scope2Emission    string `json:"scope2Emission"`
	Scope3Emission    string `json:"scope3Emission"`
	DesulfEmission    string `json:"desulfEmission"`
	TransLossEmission string `json:"transmissionLossEmission"`
	SF6Emission       string `json:"sf6Emission"`
	Signature         string `json:"signature"`
	Data              string `json:"data"`
	Status            string `json:"status"`
	TxHash            string `json:"txHash"`
	CreatedAt         string `json:"createdAt"`
	SchemaVersion     string `json:"schemaVersion"`
}

// CreateCarbonReportV2 存证完整报告快照（推荐路径）。
//
// 与 v1 CreateCarbonReport 并存，不破坏已有调用方与历史链上记录。
// 新记录带 schemaVersion="v2"，查询时可与新老记录区分。
func (cc *CarbonChaincode) CreateCarbonReportV2(
	ctx contractapi.TransactionContextInterface,
	reportID string,
	reportNo string,
	enterpriseID string,
	accountingPeriod string,
	totalEmission string,
	scope1Emission string,
	scope2Emission string,
	scope3Emission string,
	desulfEmission string,
	transmissionLossEmission string,
	sf6Emission string,
	signature string,
	reportData string,
) (*CarbonReportSnapshot, error) {
	existing, err := ctx.GetStub().GetState(reportID)
	if err != nil {
		return nil, fmt.Errorf("failed to read state: %w", err)
	}
	if existing != nil {
		return nil, fmt.Errorf("report %s already exists", reportID)
	}

	txID := ctx.GetStub().GetTxID()
	now := time.Now().Format(time.RFC3339)

	snapshot := &CarbonReportSnapshot{
		ReportID:          reportID,
		ReportNo:          reportNo,
		EnterpriseID:      enterpriseID,
		AccountingPeriod:  accountingPeriod,
		TotalEmission:     totalEmission,
		Scope1Emission:    scope1Emission,
		Scope2Emission:    scope2Emission,
		Scope3Emission:    scope3Emission,
		DesulfEmission:    desulfEmission,
		TransLossEmission: transmissionLossEmission,
		SF6Emission:       sf6Emission,
		Signature:         signature,
		Data:              reportData,
		Status:            "COMMITTED",
		TxHash:            txID,
		CreatedAt:         now,
		SchemaVersion:     "v2",
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

// QueryReportHistory returns the history of a carbon report.
//
// 返回统一的 v2 结构：历史里可能同时存在 v1 与 v2 记录（两者共用同一 state key），
// 而 Go 的 json.Unmarshal 默认**静默忽略未知字段**——把 v2 记录塞进 v1 的
// CarbonReport 结构，金额、周期、签名、schemaVersion 会被全部丢弃且不报错，
// 调用方拿到的是一条「看起来合法但金额为空」的记录。
//
// 因此这里先探测 schemaVersion 再决定用哪个结构反序列化；v1 记录显式标注
// SchemaVersion="v1" 后按可映射字段填充，缺失项留空，让调用方能区分「老记录没有」
// 与「解析失败」。
func (cc *CarbonChaincode) QueryReportHistory(ctx contractapi.TransactionContextInterface, reportID string) ([]*CarbonReportSnapshot, error) {
	resultsIterator, err := ctx.GetStub().GetHistoryForKey(reportID)
	if err != nil {
		return nil, fmt.Errorf("failed to get history: %w", err)
	}
	defer resultsIterator.Close()

	var history []*CarbonReportSnapshot
	for resultsIterator.HasNext() {
		response, err := resultsIterator.Next()
		if err != nil {
			return nil, fmt.Errorf("failed to iterate history: %w", err)
		}

		// 先取一次版本标记（只解 schemaVersion，其余字段忽略）
		var probe struct {
			SchemaVersion string `json:"schemaVersion"`
		}
		_ = json.Unmarshal(response.Value, &probe)

		if probe.SchemaVersion == "v2" {
			var snap CarbonReportSnapshot
			if err := json.Unmarshal(response.Value, &snap); err != nil {
				return nil, fmt.Errorf("failed to unmarshal v2 report %s: %w", reportID, err)
			}
			history = append(history, &snap)
			continue
		}

		// v1：字段少，逐项映射，并显式标注版本
		var legacy CarbonReport
		if err := json.Unmarshal(response.Value, &legacy); err != nil {
			return nil, fmt.Errorf("failed to unmarshal v1 report %s: %w", reportID, err)
		}
		history = append(history, &CarbonReportSnapshot{
			ReportID:      legacy.ReportID,
			EnterpriseID:  legacy.EnterpriseID,
			Data:          legacy.Data,
			Status:        legacy.Status,
			TxHash:        legacy.TxHash,
			CreatedAt:     legacy.CreatedAt,
			SchemaVersion: "v1",
		})
	}

	return history, nil
}

// CreateTradeRecord stores a new trade record on the ledger
// tradeStateKey 返回交易记录的账本 state key。
//
// 必须带 `trade~` 前缀：报告与交易的自增主键是两张表各自独立生成的
// （entity/BaseEntity 各自 IDENTITY），数值必然重叠——曾经两者都用裸数字
// String.valueOf(id) 做 key，报告 #N 上链后交易 #N 永远无法上链
// （already exists）。前缀让两者进入不同的 key 空间。
func tradeStateKey(tradeID string) string {
	return "trade~" + tradeID
}

// CreateTradeRecord stores a new trade record on the ledger.
func (cc *CarbonChaincode) CreateTradeRecord(ctx contractapi.TransactionContextInterface, tradeID string, tradeData string) (*TradeRecord, error) {
	stateKey := tradeStateKey(tradeID)
	existing, err := ctx.GetStub().GetState(stateKey)
	if err != nil {
		return nil, fmt.Errorf("failed to read state: %w", err)
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
		return nil, fmt.Errorf("failed to marshal trade: %w", err)
	}

	if err := ctx.GetStub().PutState(stateKey, tradeBytes); err != nil {
		return nil, fmt.Errorf("failed to write state: %w", err)
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

// GetTransactionByID returns transaction details by ID.
//
// Deprecated: 桩实现——不碰账本，对任意输入（包括不存在的交易号）恒返回 VALID。
// 后端零调用方；保留仅为兼容既有测试基线。严禁在业务路径调用，
// 参见 BlockchainServicePort 的禁调黑名单。
func (cc *CarbonChaincode) GetTransactionByID(ctx contractapi.TransactionContextInterface, txID string) (string, error) {
	return fmt.Sprintf(`{"txId":"%s","status":"VALID"}`, txID), nil
}

// QueryBlock returns block information by number.
//
// Deprecated: 桩实现——不碰账本，恒返回 VALID。真实区块查询走后端
// FabricBlockchainService 的区块事件流探针，不经此处。
func (cc *CarbonChaincode) QueryBlock(ctx contractapi.TransactionContextInterface, blockNumber string) (string, error) {
	return fmt.Sprintf(`{"blockNumber":"%s","status":"VALID"}`, blockNumber), nil
}

// ListTransactions returns a paginated list of transactions.
//
// Deprecated: 桩实现——恒返回空数组。真实交易列表由后端自行解析区块生成。
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
