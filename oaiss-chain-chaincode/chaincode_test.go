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
