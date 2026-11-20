import copy
import json
import tempfile
import unittest
from pathlib import Path

from publish_analysis import publish


class PublishAnalysisTests(unittest.TestCase):
    def snapshot(self, month='2026-08'):
        return {'month': month, 'title': 'AI总结', 'coverage': '完整性未确认',
                'basis': '仅已确认数据', 'overview': '本月结余70.08元',
                'dataFrom': month + '-01', 'dataThrough': month + '-13',
                'confirmedTransactionCount': 2,
                'current': {'income': '100.10', 'expense': '30.02', 'balance': '70.08'},
                'previous': None, 'categories': [{'category': '家庭', 'amount': '30.02', 'previousAmount': None}],
                'sections': [{'heading': 'AI建议', 'text': '核对2笔交易。'}]}

    def test_replaces_only_requested_month_and_keeps_exact_amounts(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            other = publish(self.snapshot('2026-07'), directory).read_bytes()
            path = publish(self.snapshot(), directory)
            replacement = self.snapshot()
            replacement['overview'] = '已更新AI总结'
            publish(replacement, directory)
            result = json.loads(path.read_text())
            self.assertEqual(result['overview'], '已更新 AI 总结')
            self.assertEqual(result['current']['balance'], '70.08')
            self.assertEqual(result['current']['balanceRate'], '70.01')
            self.assertIsNone(result['expenseChangeRate'])
            self.assertEqual((directory / '2026-07.json').read_bytes(), other)
            self.assertEqual(len(list(directory.iterdir())), 2)

    def test_invalid_data_leaves_previous_snapshot_intact(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            path = publish(self.snapshot(), directory)
            before = path.read_bytes()
            for mutate in (lambda d: d.update(month='../2026-08'),
                           lambda d: d['current'].update(balance='70.09'),
                           lambda d: d['categories'][0].update(amount='30.01'),
                           lambda d: d.update(dataThrough='2026-09-01')):
                invalid = copy.deepcopy(self.snapshot())
                mutate(invalid)
                with self.assertRaises(ValueError):
                    publish(invalid, directory)
                self.assertEqual(path.read_bytes(), before)

    def test_zero_income_and_zero_previous_expense_have_no_percentage(self):
        with tempfile.TemporaryDirectory() as temporary:
            snapshot = self.snapshot()
            snapshot['current'] = {'income': '0', 'expense': '30.02', 'balance': '-30.02'}
            snapshot['previous'] = {'income': '0', 'expense': '0', 'balance': '0'}
            snapshot['categories'][0]['previousAmount'] = '0'
            result = json.loads(publish(snapshot, Path(temporary)).read_text())
            self.assertIsNone(result['current']['balanceRate'])
            self.assertIsNone(result['expenseChangeRate'])
            self.assertEqual(result['categories'][0]['change'], '30.02')


if __name__ == '__main__':
    unittest.main()
