const collator = new Intl.Collator(undefined, { numeric: true, sensitivity: 'base' });

function compare(a, b) {
  if (typeof a === 'string') return collator.compare(a, b);
  return a < b ? -1 : a > b ? 1 : 0;
}

export function mountUsageTable(table, columns, initialColumn) {
  const head = table.createTHead().insertRow();
  const body = table.createTBody();
  let rows = [], limit, selected = initialColumn, descending = true;
  const headers = columns.map((column, index) => {
    const th = document.createElement('th');
    th.scope = 'col';
    const button = document.createElement('button');
    button.type = 'button';
    button.textContent = column.label;
    button.addEventListener('click', () => {
      descending = selected === index ? !descending : !column.text;
      selected = index;
      render();
    });
    th.append(button);
    head.append(th);
    return th;
  });
  function render() {
    headers.forEach((th, index) => {
      if (index === selected) th.setAttribute('aria-sort', descending ? 'descending' : 'ascending');
      else th.removeAttribute('aria-sort');
    });
    const column = columns[selected];
    const sorted = [...rows].sort((left, right) => {
      const order = compare(column.value(left), column.value(right));
      return (descending ? -order : order) || compare(columns[0].value(left), columns[0].value(right));
    });
    body.replaceChildren(...sorted.slice(0, limit).map(row => {
      const tr = document.createElement('tr');
      columns.forEach((c, i) => {
        const cell = document.createElement(i ? 'td' : 'th');
        if (!i) cell.scope = 'row';
        cell.append(c.display ? c.display(row) : String(c.value(row)));
        tr.append(cell);
      });
      return tr;
    }));
  }
  return {
    setRows(items, maxRows) { rows = items; limit = maxRows; render(); },
    setLabel(index, label) { headers[index].firstChild.textContent = label; },
  };
}
