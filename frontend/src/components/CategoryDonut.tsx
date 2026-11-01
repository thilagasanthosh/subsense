import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import type { CategorySpend } from '../api/types';
import { formatMoney } from '../lib/format';

export function CategoryDonut({ data }: { data: CategorySpend[] }) {
  if (data.length === 0) {
    return <p className="empty">Add a subscription to see where your money goes.</p>;
  }
  const chartData = data.map((d) => ({ ...d, value: Number(d.monthlySpend.amount) }));
  return (
    <div>
      <div style={{ height: 200 }}>
        <ResponsiveContainer>
          <PieChart>
            <Pie
              data={chartData}
              dataKey="value"
              nameKey="category"
              innerRadius={58}
              outerRadius={88}
              paddingAngle={2}
              isAnimationActive={false}
            >
              {chartData.map((d) => (
                <Cell key={d.category} fill={d.colour} />
              ))}
            </Pie>
            <Tooltip formatter={(_, __, item) => formatMoney(item.payload.monthlySpend)} />
          </PieChart>
        </ResponsiveContainer>
      </div>
      <ul className="legend">
        {data.map((d) => (
          <li key={d.category}>
            <span className="dot" style={{ background: d.colour }} />
            {d.category}
            <span className="num">{formatMoney(d.monthlySpend)}/mo</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
