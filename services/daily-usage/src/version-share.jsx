import React from 'react';
import { groupUsage, versionColor } from '../public/metrics';

const number = new Intl.NumberFormat();

export default function VersionShare({ rows }) {
  const versions = groupUsage(rows, 'versions').filter(version => version.devices > 0n);
  if (!versions.length) return <p className="version-share-empty">No version reports today.</p>;

  const total = versions.reduce((sum, version) => sum + version.devices, 0n);
  let clients = 0n;
  const slices = versions.map(version => {
    const start = Number(clients) / Number(total) * 100;
    clients += version.devices;
    const end = Number(clients) / Number(total) * 100;
    const color = versionColor(version.key);
    return { ...version, color, stop: `${color} ${start}% ${end}%` };
  });
  return <figure className="version-share">
    <figcaption>Clients by app version</figcaption>
    <div className="version-share-body">
      <div className="version-share-pie" aria-hidden="true" style={{ background: slices.length === 1
        ? slices[0].color : `conic-gradient(${slices.map(slice => slice.stop).join(', ')})` }} />
      <ul className="version-share-legend">{slices.map(slice => <li key={slice.key}>
        <span className="version-share-dot" aria-hidden="true" style={{ background: slice.color }} />
        <span>{slice.label}</span><span className="version-share-count">{number.format(slice.devices)} {slice.devices === 1n ? 'client' : 'clients'}</span>
        <strong>{slice.share === 0 ? '<0.1' : number.format(slice.share)}%</strong>
      </li>)}</ul>
    </div>
  </figure>;
}
