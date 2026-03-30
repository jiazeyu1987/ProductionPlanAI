package com.autoproduction.mvp.core;

final class StrategyProfile {
  final double urgentWeight;
  final double dueWeight;
  final double progressWeight;
  final double wipWeight;
  final double changeoverWeight;
  final double urgentGapWeight;
  final double quotaWeight;
  final double feasibleWeight;
  final double capacityWeight;

  StrategyProfile(
    double urgentWeight,
    double dueWeight,
    double progressWeight,
    double wipWeight,
    double changeoverWeight,
    double urgentGapWeight,
    double quotaWeight,
    double feasibleWeight,
    double capacityWeight
  ) {
    this.urgentWeight = urgentWeight;
    this.dueWeight = dueWeight;
    this.progressWeight = progressWeight;
    this.wipWeight = wipWeight;
    this.changeoverWeight = changeoverWeight;
    this.urgentGapWeight = urgentGapWeight;
    this.quotaWeight = quotaWeight;
    this.feasibleWeight = feasibleWeight;
    this.capacityWeight = capacityWeight;
  }
}

