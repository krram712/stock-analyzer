import React from 'react';

const GREEN_LABELS = [
  'SAFE', 'GOOD', 'CLEAN', 'STRONG', 'CHEAP', 'LEADING', 'CONSIDER',
  'HIGH', 'ACCELERATING', 'LOW', 'CONFIDENT', 'NEUTRAL-POSITIVE',
  'HIGH-QUALITY COMPOUNDER', 'WELL-CAPITALISED', 'HEALTHY', 'UNDERVALUED',
  'BUYING', 'INCREASING',
];
const YELLOW_LABELS = [
  'WATCH', 'AVERAGE', 'MODERATE', 'FAIR', 'MID-PACK', 'MIXED', 'STABLE',
  'FAIRLY VALUED', 'STEADY', 'CAUTIOUS',
];
const RED_LABELS = [
  'RISK', 'WEAK', 'STRESSED', 'LEVERAGED', 'EXPENSIVE', 'LAGGING', 'AVOID',
  'DECLINING', 'HIGH SHORT INTEREST', 'OVERVALUED', 'SELLING', 'DECREASING',
  'CONCERN', 'TURNAROUND CANDIDATE',
];

export default function Badge({ text, type }) {
  if (!text) return null;

  const upper = String(text).toUpperCase();
  let cls = 'badge badge-gray';

  if (type) {
    cls = `badge badge-${type}`;
  } else if (GREEN_LABELS.some(l => upper.includes(l))) {
    cls = 'badge badge-green';
  } else if (YELLOW_LABELS.some(l => upper.includes(l))) {
    cls = 'badge badge-yellow';
  } else if (RED_LABELS.some(l => upper.includes(l))) {
    cls = 'badge badge-red';
  } else if (upper.includes('DATA UNAVAILABLE')) {
    cls = 'badge badge-orange';
  }

  return <span className={cls}>{text}</span>;
}
