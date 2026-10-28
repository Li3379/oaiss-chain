export interface CoalOxidationRateRequest {
  slagWeight: number
  slagCarbonContent: number
  flyAshWeight: number
  flyAshCarbonContent: number
  dustRemovalEfficiency?: number
  coalConsumption: number
  netCalorificValue: number
  carbonContent: number
}

/** Power generation calculation request — flat structure matching backend DTO */
export interface PowerGenerationCalculationRequest {
  rawCoalFc: number | null
  rawCoalNcv: number | null
  rawCoalCc: number | null
  rawCoalOf: number | null
  cleanedCoalFc: number | null
  cleanedCoalNcv: number | null
  cleanedCoalCc: number | null
  cleanedCoalOf: number | null
  otherWashedCoalFc: number | null
  otherWashedCoalNcv: number | null
  otherWashedCoalCc: number | null
  otherWashedCoalOf: number | null
  briquetteFc: number | null
  briquetteNcv: number | null
  briquetteCc: number | null
  briquetteOf: number | null
  otherCoalFc: number | null
  otherCoalNcv: number | null
  otherCoalCc: number | null
  otherCoalOf: number | null
  carbonateConsumed: number | null
  desulfEmissionFactor: number | null
  desulfConversionRate: number | null
  reportingYear: number
  enterpriseName: string
}

/** Fuel emission detail */
export interface FuelEmissionDetail {
  fuelType: string
  fuelConsumption: number
  netCalorificValue: number
  carbonContent: number
  oxidationRate: number
  emission: number
}

/** Power generation calculation response */
export interface PowerGenerationCalculationResponse {
  totalEmission: number
  combustionEmission: number
  desulfurizationEmission: number
  fuelDetails: FuelEmissionDetail[]
  reportingYear: string
  enterpriseName: string
  formulaReference: string
  calculatedAt: string
}

/** Power grid calculation request */
export interface PowerGridCalculationRequest {
  transmissionVolume: number
  lineLossRate: number
  gridEmissionFactor: number
  generationVolume: number | null
  importedElectricity: number | null
  exportedElectricity: number | null
  importEmissionFactor: number | null
  reportingYear: number
  enterpriseName: string
}

/** Power grid calculation response */
export interface PowerGridCalculationResponse {
  totalEmission: number
  transmissionLossEmission: number
  importedEmission: number
  transmissionLoss: number
  formulaReference: string
  reportingYear: string
  enterpriseName: string
  calculatedAt: string
}