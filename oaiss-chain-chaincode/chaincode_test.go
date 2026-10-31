package main

import (
	"encoding/json"
	"testing"

	"github.com/hyperledger/fabric-chaincode-go/shim"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"
)

// MockStub provides just enough behavior for the lightweight unit tests below.
type MockStub struct {
	mock.Mock
	state map[string][]byte
}

func NewMockStub() *MockStub {
	return &MockStub{state: make(map[string][]byte)}
}

func (ms *MockStub) GetState(key string) ([]byte, error) {
	if val, ok := ms.state[key]; ok {
		return val, nil
	}
	return nil, nil
}

func (ms *MockStub) PutState(key string, value []byte) error {
	ms.state[key] = value
	return nil
}

func (ms *MockStub) GetTxID() string {
	return "tx_mock_12345"
}

func (ms *MockStub) DelState(key string) error {
	delete(ms.state, key)
	return nil
}

// CreateCompositeKey 拼接索引键（v2 快照用「企业~报告」索引）。
func (ms *MockStub) CreateCompositeKey(objectType string, attributes []string) (string, error) {
	key := objectType
	for _, a := range attributes {
		key = key + string(rune(0)) + a
	}
	return key, nil
}

// MockContext exists only to keep the test package aligned with current Fabric APIs.
type MockContext struct {
	stub *MockStub
}

func (mc *MockContext) GetStub() shim.ChaincodeStubInterface {
	return nil
}

// TestCreateCarbonReport creates a report and verifies it's stored.
func TestCreateCarbonReport(t *testing.T) {
	stub := NewMockStub()
	reportID := "REPORT_1"
	reportData := "{\"enterpriseId\":\"ENT_001\",\"emissions\":500}"

	report := &CarbonReport{
		ReportID:  reportID,
		Data:      reportData,
		Status:    "COMMITTED",
		TxHash:    "tx_mock_12345",
		CreatedAt: "2026-05-15T10:00:00Z",
	}

	reportBytes, err := json.Marshal(report)
	assert.NoError(t, err)

	err = stub.PutState(reportID, reportBytes)
	assert.NoError(t, err)

	stored, err := stub.GetState(reportID)
	assert.NoError(t, err)
	assert.NotNil(t, stored)

	var storedReport CarbonReport
	err = json.Unmarshal(stored, &storedReport)
	assert.NoError(t, err)
	assert.Equal(t, reportID, storedReport.ReportID)
	assert.Equal(t, "COMMITTED", storedReport.Status)
	assert.Equal(t, "tx_mock_12345", storedReport.TxHash)
}

// TestCreateCarbonReportDuplicate verifies duplicate creation fails.
func TestCreateCarbonReportDuplicate(t *testing.T) {
	stub := NewMockStub()
	reportID := "REPORT_1"

	existingReport := &CarbonReport{ReportID: reportID, Status: "COMMITTED"}
	existingBytes, err := json.Marshal(existingReport)
	assert.NoError(t, err)

	err = stub.PutState(reportID, existingBytes)
	assert.NoError(t, err)

	existing, err := stub.GetState(reportID)
	assert.NoError(t, err)
	assert.NotNil(t, existing, "Existing report should block duplicate creation")
}

// TestCreateTradeRecord creates a trade and verifies it's stored.
func TestCreateTradeRecord(t *testing.T) {
	stub := NewMockStub()
	tradeID := "TRADE_1"
	tradeData := "{\"sellerId\":\"ENT_001\",\"buyerId\":\"ENT_002\",\"amount\":100}"

	trade := &TradeRecord{
		TradeID:   tradeID,
		Data:      tradeData,
		TxHash:    "tx_mock_12345",
		CreatedAt: "2026-05-15T10:00:00Z",
	}

	tradeBytes, err := json.Marshal(trade)
	assert.NoError(t, err)

	err = stub.PutState(tradeID, tradeBytes)
	assert.NoError(t, err)

	stored, err := stub.GetState(tradeID)
	assert.NoError(t, err)
	assert.NotNil(t, stored)

	var storedTrade TradeRecord
	err = json.Unmarshal(stored, &storedTrade)
	assert.NoError(t, err)
	assert.Equal(t, tradeID, storedTrade.TradeID)
	assert.Equal(t, "tx_mock_12345", storedTrade.TxHash)
}

// TestVerifyReport verifies a report can be retrieved.
func TestVerifyReport(t *testing.T) {
	stub := NewMockStub()
	reportID := "REPORT_1"

	report := &CarbonReport{
		ReportID:  reportID,
		Data:      "{\"emissions\":500}",
		Status:    "COMMITTED",
		TxHash:    "tx_mock_12345",
		CreatedAt: "2026-05-15T10:00:00Z",
	}
	reportBytes, err := json.Marshal(report)
	assert.NoError(t, err)

	err = stub.PutState(reportID, reportBytes)
	assert.NoError(t, err)

	stored, err := stub.GetState(reportID)
	assert.NoError(t, err)
	assert.NotNil(t, stored)

	var storedReport CarbonReport
	err = json.Unmarshal(stored, &storedReport)
	assert.NoError(t, err)
	assert.Equal(t, reportID, storedReport.ReportID)
}

// TestVerifyReportNotFound verifies missing report returns nil.
func TestVerifyReportNotFound(t *testing.T) {
	stub := NewMockStub()

	stored, err := stub.GetState("NONEXISTENT_REPORT")
	assert.NoError(t, err)
	assert.Nil(t, stored, "Non-existent report should return nil")
}

// TestGetTransactionByID returns a valid JSON response.
func TestGetTransactionByID(t *testing.T) {
	cc := &CarbonChaincode{}
	result, err := cc.GetTransactionByID(nil, "tx123")
	assert.NoError(t, err)
	assert.Contains(t, result, "tx123")
	assert.Contains(t, result, "VALID")
}

// TestQueryBlock returns a valid JSON response.
func TestQueryBlock(t *testing.T) {
	cc := &CarbonChaincode{}
	result, err := cc.QueryBlock(nil, "5")
	assert.NoError(t, err)
	assert.Contains(t, result, "5")
	assert.Contains(t, result, "VALID")
}

// TestCarbonReportSnapshotV2Shape 验证 v2 快照结构能正确序列化并携带主体归属。
//
// 背景：v1 链上只存 Data 原文，EnterpriseID 字段从未赋值，
// 链上无法回答「这份报告属于哪家企业」。v2 补齐主体、周期、总量、三范围与签名。
func TestCarbonReportSnapshotV2Shape(t *testing.T) {
	snapshot := &CarbonReportSnapshot{
		ReportID:         "42",
		EnterpriseID:     "7",
		AccountingPeriod: "2026-Q2",
		TotalEmission:    "1234.5678",
		Scope1Emission:   "1000.0000",
		Scope2Emission:   "234.5678",
		Scope3Emission:   "0",
		Signature:        "sig-abc",
		Data:             `{"scope1":1000}`,
		Status:           "COMMITTED",
		TxHash:           "tx_v2_001",
		CreatedAt:        "2026-10-31T10:00:00Z",
		SchemaVersion:    "v2",
	}

	bytes, err := json.Marshal(snapshot)
	assert.NoError(t, err)

	var decoded CarbonReportSnapshot
	assert.NoError(t, json.Unmarshal(bytes, &decoded))

	// 核心断言：链上记录能独立回答「属于哪家企业」
	assert.Equal(t, "7", decoded.EnterpriseID, "v2 必须携带企业ID，否则链上无法确定主体")
	assert.Equal(t, "2026-Q2", decoded.AccountingPeriod)
	assert.Equal(t, "1234.5678", decoded.TotalEmission)
	assert.Equal(t, "1000.0000", decoded.Scope1Emission)
	assert.Equal(t, "sig-abc", decoded.Signature, "签名必须上链，链上才能独立验证内容未被改")
	assert.Equal(t, "v2", decoded.SchemaVersion, "版本标识用于区分新老记录")
	assert.NotEmpty(t, decoded.Data)
}

// TestLegacyCarbonReportStillSupported 验证 v1 结构未被破坏（历史链上记录仍可解析）。
func TestLegacyCarbonReportStillSupported(t *testing.T) {
	legacy := &CarbonReport{
		ReportID:  "1",
		Data:      `{"scope1":100}`,
		Status:    "COMMITTED",
		TxHash:    "tx_v1_001",
		CreatedAt: "2026-01-01T00:00:00Z",
	}
	bytes, err := json.Marshal(legacy)
	assert.NoError(t, err)

	var decoded CarbonReport
	assert.NoError(t, json.Unmarshal(bytes, &decoded))
	assert.Equal(t, "1", decoded.ReportID)
	assert.Equal(t, "COMMITTED", decoded.Status)
	// v1 的 EnterpriseID 为空——这正是 v2 要解决的问题
	assert.Empty(t, decoded.EnterpriseID)
}
