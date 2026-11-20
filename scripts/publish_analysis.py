"""Publish a conversation-written monthly snapshot to the local analysis column."""
import argparse
import json
import os
import re
import tempfile
from datetime import datetime
from decimal import Decimal
from pathlib import Path
from zoneinfo import ZoneInfo


def text(value):
    value = value.replace('—', '--')
    value = re.sub(r'([\u4e00-\u9fff])([A-Za-z0-9])', r'\1 \2', value)
    value = re.sub(r'([A-Za-z0-9])([\u4e00-\u9fff])', r'\1 \2', value)
    for mark, replacement in {',': '，', ':': '：', ';': '；', '?': '？', '!': '！'}.items():
        value = re.sub(r'(?<=[\u4e00-\u9fff])' + re.escape(mark), replacement, value)
    return value


def publish(document, directory):
    month = document['month']
    if not re.fullmatch(r'\d{4}-(0[1-9]|1[0-2])', month):
        raise ValueError('月份格式应为 YYYY-MM')
    datetime.strptime(month, '%Y-%m')
    for key in ('title', 'coverage', 'basis', 'overview', 'dataFrom', 'dataThrough'):
        if not isinstance(document[key], str) or not document[key].strip():
            raise ValueError('缺少必要的总结文本')
        document[key] = text(document[key])
    for key in ('dataFrom', 'dataThrough'):
        if document[key][:7] != month:
            raise ValueError('交易日期必须属于总结月份')
        datetime.strptime(document[key], '%Y-%m-%d')
    if document['dataFrom'] > document['dataThrough']:
        raise ValueError('交易日期范围无效')
    if not document['sections'] or not isinstance(document['confirmedTransactionCount'], int) or document['confirmedTransactionCount'] < 0:
        raise ValueError('总结段落或交易笔数无效')
    for section in document['sections']:
        section['heading'] = text(section['heading'])
        section['text'] = text(section['text'])
    for totals in (document['current'], document['previous']):
        if totals is None:
            continue
        income, expense, balance = (Decimal(totals[key]) for key in ('income', 'expense', 'balance'))
        if not all(value.is_finite() for value in (income, expense, balance)) or income < 0 or expense < 0 or income - expense != balance:
            raise ValueError('收支结余不一致')
    current = document['current']
    previous = document['previous']
    for totals in (current, previous):
        if totals is not None:
            income, balance = Decimal(totals['income']), Decimal(totals['balance'])
            totals['balanceRate'] = str((balance / income * 100).quantize(Decimal('.01'))) if income else None
    expense = Decimal(current['expense'])
    previous_expense = Decimal(previous['expense']) if previous else None
    document['expenseChangeRate'] = str(((expense - previous_expense) / previous_expense * 100).quantize(Decimal('.01'))) if previous_expense else None
    categories = document['categories']
    names = [row['category'] for row in categories]
    if len(names) != len(set(names)) or sum((Decimal(row['amount']) for row in categories), Decimal(0)) != expense:
        raise ValueError('分类支出与月度支出不一致')
    if previous is not None and sum((Decimal(row['previousAmount']) for row in categories), Decimal(0)) != previous_expense:
        raise ValueError('上月分类支出与月度支出不一致')
    for row in categories:
        amount = Decimal(row['amount'])
        if not amount.is_finite() or amount < 0:
            raise ValueError('分类金额无效')
        row['share'] = str((amount / expense * 100).quantize(Decimal('.01'))) if expense else None
        row['change'] = str(amount - Decimal(row['previousAmount'])) if previous is not None else None
    document['generatedAt'] = datetime.now(ZoneInfo('Asia/Shanghai')).strftime('%Y-%m-%d %H:%M:%S +08:00')
    directory.mkdir(parents=True, exist_ok=True)
    target = directory / (month + '.json')
    # Readers see either the complete previous snapshot or the complete replacement.
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', dir=directory, suffix='.tmp', delete=False) as output:
            temporary = Path(output.name)
            json.dump(document, output, ensure_ascii=False, indent=2, allow_nan=False)
            output.write('\n')
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, target)
    finally:
        if temporary is not None and temporary.exists():
            temporary.unlink()
    return target


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('snapshot', type=Path)
    parser.add_argument('--directory', type=Path, default=Path(__file__).resolve().parents[1] / 'data/analyses')
    args = parser.parse_args()
    try:
        document = json.loads(args.snapshot.read_text(encoding='utf-8'), parse_float=str)
        print(publish(document, args.directory))
    except (ValueError, KeyError, TypeError, ArithmeticError):
        parser.exit(1, '总结格式或报表金额校验失败，原归档未更改。\n')
