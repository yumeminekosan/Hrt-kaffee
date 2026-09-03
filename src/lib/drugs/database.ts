import type { DrugDatabase } from './types';

export const DRUG_DB: DrugDatabase = {
  E2_oral: {
    name: 'Estradiol Oral',
    route: 'oral', parameterization: 'population',
    therapeutic: [50, 200], unit: 'pg/mL',
    CL: 60, Vd: 60, ka: 0.04, F: 0.03,
    halfLife: 1, halfLifeApparent: 17,
    doseUnit: 'mg', intervalUnit: 'h',
    defaultDose: 2, defaultInterval: 12,
    ref: 'PMID: 1548642'
  },
  E2V_oral: {
    name: 'Estradiol Valerate Oral',
    route: 'oral', parameterization: 'population',
    therapeutic: [50, 200], unit: 'pg/mL',
    CL: 60, Vd: 60, ka: 0.04, F: 0.03,
    activeMoietyFraction: 0.764,
    halfLife: 1, halfLifeApparent: 17,
    doseUnit: 'mg', intervalUnit: 'h',
    defaultDose: 2, defaultInterval: 12,
    ref: 'PMID: 9793623'
  },
  E2_subl: {
    name: 'Estradiol Sublingual',
    route: 'sublingual', parameterization: 'illustrative',
    therapeutic: [50, 200], unit: 'pg/mL',
    CL: 15, Vd: 150, ka: 2.5, F: 0.10,
    halfLife: 12,
    doseUnit: 'mg', intervalUnit: 'h',
    defaultDose: 2, defaultInterval: 12,
    ref: 'PMID: 9052581'
  },
  E2_td: {
    name: 'Estradiol Transdermal Patch',
    route: 'transdermal', parameterization: 'population',
    therapeutic: [50, 200], unit: 'pg/mL',
    CL: 10, Vd: 150, ka: 0.03, F: 0.85,
    halfLife: 36,
    doseUnit: 'mg', intervalUnit: 'h',
    defaultDose: 0.05, defaultInterval: 84,
    ref: 'PMID: 9689205'
  },
  E2_td_gel: {
    name: 'Estradiol Transdermal Gel',
    route: 'transdermal', parameterization: 'illustrative',
    therapeutic: [50, 200], unit: 'pg/mL',
    CL: 12, Vd: 180, ka: 0.15, F: 0.10,
    halfLife: 24,
    doseUnit: 'mg', intervalUnit: 'h',
    defaultDose: 1.5, defaultInterval: 24,
    ref: 'PMID: 17143811'
  },
  E2V: {
    name: 'Estradiol Valerate IM',
    route: 'intramuscular-depot', parameterization: 'population',
    therapeutic: [100, 400], unit: 'pg/mL',
    CL: 100, Vd: 2400, ka: 0.012, F: 0.85,
    activeMoietyFraction: 0.764,
    halfLife: 120,
    doseUnit: 'mg', intervalUnit: 'h',
    defaultDose: 5, defaultInterval: 168,
    ref: 'PMID: 7169965'
  },
  E2C: {
    name: 'Estradiol Cypionate IM',
    route: 'intramuscular-depot', parameterization: 'illustrative',
    therapeutic: [100, 400], unit: 'pg/mL',
    CL: 120, Vd: 2800, ka: 0.006, F: 0.90,
    activeMoietyFraction: 0.687,
    halfLife: 192,
    doseUnit: 'mg', intervalUnit: 'h',
    defaultDose: 5, defaultInterval: 168,
    ref: 'Illustrative parameters; route evidence PMID: 10640167'
  },
  E2E: {
    name: 'Estradiol Enanthate IM',
    route: 'intramuscular-depot', parameterization: 'illustrative',
    therapeutic: [100, 400], unit: 'pg/mL',
    CL: 5, Vd: 2500, ka: 0.004, F: 0.92,
    activeMoietyFraction: 0.708,
    halfLife: 240,
    doseUnit: 'mg', intervalUnit: 'h',
    defaultDose: 5, defaultInterval: 168,
    ref: 'Illustrative parameterization; no individualized calibration'
  },
  MPA_oral: {
    name: 'Medroxyprogesterone Acetate Oral',
    route: 'oral', parameterization: 'population',
    therapeutic: [1, 10], unit: 'ng/mL',
    CL: 20, Vd: 35, ka: 1.2, F: 0.95,
    halfLife: 30,
    doseUnit: 'mg', intervalUnit: 'h',
    defaultDose: 10, defaultInterval: 24,
    ref: 'DrugBank DB00603 | Pfizer PROVERA'
  },
  CPA_oral: {
    name: 'Cyproterone Acetate Oral',
    route: 'oral', parameterization: 'population',
    therapeutic: [50, 300], unit: 'ng/mL',
    CL: 5, Vd: 3, ka: 0.8, F: 0.88,
    halfLife: 60,
    doseUnit: 'mg', intervalUnit: 'h',
    defaultDose: 25, defaultInterval: 24,
    ref: 'PMID: 3127499'
  },
  TEST_En: {
    name: 'Testosterone Enanthate IM',
    route: 'intramuscular-depot', parameterization: 'population',
    therapeutic: [300, 1000], unit: 'ng/dL',
    CL: 80, Vd: 1900, ka: 0.015, F: 0.65,
    activeMoietyFraction: 0.720,
    halfLife: 96,
    doseUnit: 'mg', intervalUnit: 'h',
    defaultDose: 100, defaultInterval: 168,
    ref: 'PMC4721027 | PMC9293229'
  },
  TEST_Cy: {
    name: 'Testosterone Cypionate IM',
    route: 'intramuscular-depot', parameterization: 'illustrative',
    therapeutic: [300, 1000], unit: 'ng/dL',
    CL: 6, Vd: 900, ka: 0.012, F: 0.65,
    activeMoietyFraction: 0.699,
    halfLife: 120,
    doseUnit: 'mg', intervalUnit: 'h',
    defaultDose: 100, defaultInterval: 168,
    ref: 'Illustrative parameterization; no individualized calibration'
  }
};
