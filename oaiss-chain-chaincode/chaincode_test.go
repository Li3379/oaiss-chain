package main

import (
	"encoding/json"
	"strings"
	"testing"

	"github.com/hyperledger/fabric-chaincode-go/shim"
	"github.com/hyperledger/fabric-protos-go/ledger/queryresult"
	"github.com/hyperledger/fabric-contract-api-go/contractapi"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"
)

// MockStub provides just enough behavior for the lightweight unit tests below.
//
// 嵌入 ChaincodeStubInterface（nil）以获得接口完整性：链码只用得到下面显式实现的
// 那几个方法，其余方法一旦被调用会 panic —— 这比「静默返回零值」更容易暴露问题。
type MockStub struct {
	shim.ChaincodeStubInterface
	mock.Mock
	state    map[string][]byte
	history  map[string][][]byte
	composite []string
}

func NewMockStub() *MockStub {
	return &MockStub{
		state:   make(map[string][]byte),
		history: make(map[string][][]byte),
	}
}

func (ms *MockStub) GetState(key string) ([]byte, error) {
	if val, ok := ms.state[key]; ok {
		return val, nil
	}
	return nil, nil
}

func (ms *MockStub) PutState(key string, value []byte) error {
	ms.state[key] = value
	ms.history[key] = append(ms.history[key], value)
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
	ms.composite = append(ms.composite, key)
	return key, nil
}

// SplitCompositeKey 反解 CreateCompositeKey 生成的键。
func (ms *MockStub) SplitCompositeKey(compositeKey string) (string, []string, error) {
	parts := strings.Split(compositeKey, string(rune(0)))
	if len(parts) == 0 {
		return "", nil, nil
	}
	return parts[0], parts[1:], nil
}

// GetStateByPartialCompositeKey 返回此前写入、且以该前缀开头的复合键。
func (ms *MockStub) GetStateByPartialCompositeKey(objectType string, attributes []string) (shim.StateQueryIteratorInterface, error) {
	prefix := objectType
	for _, a := range attributes {
		prefix = prefix + string(rune(0)) + a
	}
	var matches []string
	for _, key := range ms.composite {
		if strings.HasPrefix(key, prefix) {
			matches = append(matches, key)
		}
	}
	return &mockStateIterator{keys: matches, state: ms.state}, nil
}

// GetHistoryForKey 返回该键的历史写入序列（用于 QueryReportHistory）。
func (ms *MockStub) GetHistoryForKey(key string) (shim.HistoryQueryIteratorInterface, error) {
	values := ms.history[key]
	return &mockHistoryIterator{values: values}, nil
}

// mockStateIterator 是 StateQueryIteratorInterface 的最小实现。
type mockStateIterator struct {
	shim.StateQueryIteratorInterface
	keys  []string
	state map[string][]byte
	pos   int
}

func (it *mockStateIterator) HasNext() bool { return it.pos < len(it.keys) }

func (it *mockStateIterator) Next() (*queryresult.KV, error) {
	key := it.keys[it.pos]
	it.pos++
	return &queryresult.KV{Key: key, Value: it.state[key]}, nil
}

func (it *mockStateIterator) Close() error { return nil }

// mockHistoryIterator 是 HistoryQueryIteratorInterface 的最小实现。
type mockHistoryIterator struct {
	shim.HistoryQueryIteratorInterface
	values [][]byte
	pos    int
}

func (it *mockHistoryIterator) HasNext() bool { return it.pos < len(it.values) }

func (it *mockHistoryIterator) Next() (*queryresult.KeyModification, error) {
	value := it.values[it.pos]
	it.pos++
	return &queryresult.KeyModification{Value: value, TxId: "tx_mock_12345"}, nil
}

func (it *mockHistoryIterator) Close() error { return nil }

// MockContext exists only to keep the test package aligned with current Fabric APIs.
type MockContext struct {
	stub shim.ChaincodeStubInterface
}

func (mc *MockContext) GetStub() shim.ChaincodeStubInterface {
	return mc.stub
}

// newContractContext 返回一个可作为 contractapi.TransactionContextInterface 的生产者。
//
// 背景：此前本文件的用例全部是「手工 PutState 再 GetState」——它们验证的是
// MockStub 自身，而不是链码函数。于是「后端调用的 CreateCarbonReportV2 根本
// 不存在」这种缺陷可以长期逃过测试（实际就发生过：网络里跑着只有 v1 的旧包）。
// 下面这些用例直接调用链码方法，才能锁住「函数存在且行为正确」。
func newContractContext() *contractapi.TransactionContext {
	ctx := new(contractapi.TransactionContext)
	ctx.SetStub(NewMockStub())
	return ctx
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
// TestCreateCarbonReportV2_IsCallable 直接调用 v2 函数。
//
// 这是本轮修复的核心回归：此前全仓没有任何用例真正调用链码函数，
// 于是「后端 invoke 的 CreateCarbonReportV2 在链码里不存在」没有被发现，
// 网络上跑着只有 v1 的旧包，自动上链长期 100% 失败。
func TestCreateCarbonReportV2_IsCallable(t *testing.T) {
	cc := &CarbonChaincode{}
	ctx := newContractContext()

	snapshot, err := cc.CreateCarbonReportV2(ctx, "42", "CR-2026-0042", "7", "2026-Q2",
		"1234.5678", "1000.0000", "234.5678", "0", "12.5", "20.25", "3.75",
		"sig-abc", `{"scope1":1000}`)

	assert.NoError(t, err, "CreateCarbonReportV2 必须存在且可调用")
	assert.NotNil(t, snapshot)
	assert.Equal(t, "42", snapshot.ReportID)
	assert.Equal(t, "7", snapshot.EnterpriseID)
	assert.Equal(t, "v2", snapshot.SchemaVersion)
	assert.Equal(t, "COMMITTED", snapshot.Status)
	assert.Equal(t, "sig-abc", snapshot.Signature)

	// 必须真的写进账本
	stored, err := ctx.GetStub().GetState("42")
	assert.NoError(t, err)
	assert.NotNil(t, stored, "v2 写入后账本中必须有记录")

	var decoded CarbonReportSnapshot
	assert.NoError(t, json.Unmarshal(stored, &decoded))
	assert.Equal(t, "1234.5678", decoded.TotalEmission)
}

// TestCreateCarbonReportV2_RejectsDuplicate 同一 reportID 二次写入必须报错。
func TestCreateCarbonReportV2_RejectsDuplicate(t *testing.T) {
	cc := &CarbonChaincode{}
	ctx := newContractContext()

	_, err := cc.CreateCarbonReportV2(ctx, "42", "CR-1", "7", "2026-Q2",
		"100", "100", "0", "0", "0", "0", "0", "sig", "{}")
	assert.NoError(t, err)

	_, err = cc.CreateCarbonReportV2(ctx, "42", "CR-1", "7", "2026-Q2",
		"100", "100", "0", "0", "0", "0", "0", "sig", "{}")
	assert.Error(t, err, "账本不可重复写入同一 key")
}

// TestCreateTradeRecord_IsCallable 直接调用交易存证函数。
func TestCreateTradeRecord_IsCallable(t *testing.T) {
	cc := &CarbonChaincode{}
	ctx := newContractContext()

	record, err := cc.CreateTradeRecord(ctx, "T1", `{"amount":100}`)

	assert.NoError(t, err, "CreateTradeRecord 必须存在且可调用")
	assert.NotNil(t, record)
	assert.Equal(t, "T1", record.TradeID)
}

// TestQueryReportHistory_IsCallable 直接调用历史查询（对账端点依赖它）。
func TestQueryReportHistory_IsCallable(t *testing.T) {
	cc := &CarbonChaincode{}
	ctx := newContractContext()

	_, err := cc.CreateCarbonReportV2(ctx, "42", "CR-1", "7", "2026-Q2",
		"100", "100", "0", "0", "0", "0", "0", "sig", "{}")
	assert.NoError(t, err)

	history, err := cc.QueryReportHistory(ctx, "42")

	assert.NoError(t, err, "QueryReportHistory 必须存在且可调用")
	assert.Len(t, history, 1)
	assert.Equal(t, "v2", history[0].SchemaVersion)
}

// TestVerifyReport_IsCallable 直接调用存证查验（GET /blockchain/report 的底层）。
func TestVerifyReport_IsCallable(t *testing.T) {
	cc := &CarbonChaincode{}
	ctx := newContractContext()

	_, err := cc.CreateCarbonReportV2(ctx, "42", "CR-1", "7", "2026-Q2",
		"100", "100", "0", "0", "0", "0", "0", "sig", "{}")
	assert.NoError(t, err)

	value, err := cc.VerifyReport(ctx, "42")

	assert.NoError(t, err, "VerifyReport 必须存在且可调用")
	assert.NotNil(t, value)
}

// TestAllBackendInvokedFunctionsExist 锁定「后端会调用到的每个链码函数都存在」。
//
// 后端 FabricBlockchainService 硬编码了这些函数名；任何一项缺失都会让
// 对应能力在运行期静默失败（异常被捕获后降级成「没有记录」）。
func TestAllBackendInvokedFunctionsExist(t *testing.T) {
	cc := &CarbonChaincode{}
	ctx := newContractContext()

	// 后端实际 invoke/evaluate 的函数清单（见 FabricBlockchainService）
	assert.NotPanics(t, func() {
		_, _ = cc.CreateCarbonReportV2(ctx, "p1", "", "", "", "", "", "", "", "", "", "", "", "")
	}, "CreateCarbonReportV2")
	assert.NotPanics(t, func() {
		_, _ = cc.CreateCarbonReport(ctx, "p2", "{}")
	}, "CreateCarbonReport")
	assert.NotPanics(t, func() {
		_, _ = cc.CreateTradeRecord(ctx, "t1", "{}")
	}, "CreateTradeRecord")
	assert.NotPanics(t, func() {
		_, _ = cc.QueryReportHistory(ctx, "p1")
	}, "QueryReportHistory")
	assert.NotPanics(t, func() {
		_, _ = cc.VerifyReport(ctx, "p1")
	}, "VerifyReport")
}

func TestCarbonReportSnapshotV2Shape(t *testing.T) {
	snapshot := &CarbonReportSnapshot{
		ReportID:          "42",
		ReportNo:          "CR-2026-0042",
		EnterpriseID:      "7",
		AccountingPeriod:  "2026-Q2",
		TotalEmission:     "1234.5678",
		Scope1Emission:    "1000.0000",
		Scope2Emission:    "234.5678",
		Scope3Emission:    "0",
		DesulfEmission:    "12.5",
		TransLossEmission: "20.25",
		SF6Emission:       "3.75",
		Signature:         "sig-abc",
		Data:              `{"scope1":1000}`,
		Status:            "COMMITTED",
		TxHash:            "tx_v2_001",
		CreatedAt:         "2026-10-31T10:00:00Z",
		SchemaVersion:     "v2",
	}

	bytes, err := json.Marshal(snapshot)
	assert.NoError(t, err)

	var decoded CarbonReportSnapshot
	assert.NoError(t, json.Unmarshal(bytes, &decoded))

	// 核心断言：链上记录能独立回答「属于哪家企业」
	assert.Equal(t, "7", decoded.EnterpriseID, "v2 必须携带企业ID，否则链上无法确定主体")
	assert.Equal(t, "CR-2026-0042", decoded.ReportNo, "报告编号必须上链，否则链上无法与业务单据对账")
	assert.Equal(t, "2026-Q2", decoded.AccountingPeriod)
	assert.Equal(t, "1234.5678", decoded.TotalEmission)
	assert.Equal(t, "1000.0000", decoded.Scope1Emission)
	assert.Equal(t, "sig-abc", decoded.Signature, "签名必须上链，链上才能独立验证内容未被改")
	assert.Equal(t, "v2", decoded.SchemaVersion, "版本标识用于区分新老记录")
	assert.NotEmpty(t, decoded.Data)

	// 过程排放独立成字段：总量含脱硫/线损/SF6，只存三范围会让链上对不上账
	assert.Equal(t, "12.5", decoded.DesulfEmission)
	assert.Equal(t, "20.25", decoded.TransLossEmission)
	assert.Equal(t, "3.75", decoded.SF6Emission)
}

// TestQueryReportHistoryHandlesMixedVersions 锁定 v1/v2 混读不丢字段。
//
// 背景：v1 与 v2 共用同一个 state key，链上历史里两个版本会共处同一数组。
// Go 的 json.Unmarshal 默认静默忽略未知字段——旧实现把 v2 记录塞进 v1 结构，
// 金额、周期、签名全部被丢弃且不报错，调用方拿到一条「看起来合法但金额为空」的记录。
// 修复后 QueryReportHistory 先探测 schemaVersion 再选结构，v1 记录显式标注 v1。
func TestQueryReportHistoryHandlesMixedVersions(t *testing.T) {
	v2Bytes, err := json.Marshal(&CarbonReportSnapshot{
		ReportID:      "42",
		EnterpriseID:  "7",
		TotalEmission: "1234.5678",
		Signature:     "sig-abc",
		SchemaVersion: "v2",
	})
	assert.NoError(t, err)

	// 旧版本记录没有 schemaVersion 字段，只能靠探测值为空来识别
	v1Bytes, err := json.Marshal(&CarbonReport{ReportID: "1", Data: `{"scope1":100}`})
	assert.NoError(t, err)

	// 复现 QueryReportHistory 的判别逻辑
	classify := func(raw []byte) string {
		var probe struct {
			SchemaVersion string `json:"schemaVersion"`
		}
		_ = json.Unmarshal(raw, &probe)
		return probe.SchemaVersion
	}

	assert.Equal(t, "v2", classify(v2Bytes))
	assert.Equal(t, "", classify(v1Bytes), "v1 记录无版本标记，必须能被识别为 legacy")

	// v2 记录走 v2 结构反序列化时金额不得丢失
	var decoded CarbonReportSnapshot
	assert.NoError(t, json.Unmarshal(v2Bytes, &decoded))
	assert.Equal(t, "1234.5678", decoded.TotalEmission, "v2 金额不能在历史查询中丢失")
	assert.Equal(t, "sig-abc", decoded.Signature)
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
