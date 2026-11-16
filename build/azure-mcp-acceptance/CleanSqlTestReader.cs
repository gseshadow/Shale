// Offline regression fixture. No SQL provider, credentials or network operations.
using System;
using System.Collections;
using System.Data.Common;
public sealed class CleanSqlTestReader : DbDataReader {
    private bool read=false;
    private readonly bool failOnRead;
    public CleanSqlTestReader(bool failOnRead) { this.failOnRead=failOnRead; }
    public override bool Read() { if(read){if(failOnRead)throw new InvalidOperationException("private read error");return false;}read=true;return true; }
    public override bool NextResult() { throw new InvalidOperationException("private late result error"); }
    public override int FieldCount => 1;
    public override string GetName(int ordinal) => "AcceptanceResult";
    public override object GetValue(int ordinal) => "ACCEPTANCE_SOURCE_OK:21-proof:1";
    public override bool IsDBNull(int ordinal) => false;
    public override int Depth => 0;
    public override bool HasRows => true;
    public override bool IsClosed => false;
    public override int RecordsAffected => -1;
    public override object this[int ordinal] => GetValue(ordinal);
    public override object this[string name] => GetValue(0);
    public override int GetOrdinal(string name) => 0;
    public override string GetDataTypeName(int ordinal) => "varchar";
    public override Type GetFieldType(int ordinal) => typeof(string);
    public override int GetValues(object[] values) {values[0]=GetValue(0);return 1;}
    public override IEnumerator GetEnumerator() => throw new NotSupportedException();
    public override bool GetBoolean(int i) => throw new NotSupportedException();
    public override byte GetByte(int i) => throw new NotSupportedException();
    public override long GetBytes(int i,long a,byte[] b,int c,int d) => throw new NotSupportedException();
    public override char GetChar(int i) => throw new NotSupportedException();
    public override long GetChars(int i,long a,char[] b,int c,int d) => throw new NotSupportedException();
    public override Guid GetGuid(int i) => throw new NotSupportedException();
    public override short GetInt16(int i) => throw new NotSupportedException();
    public override int GetInt32(int i) => throw new NotSupportedException();
    public override long GetInt64(int i) => throw new NotSupportedException();
    public override float GetFloat(int i) => throw new NotSupportedException();
    public override double GetDouble(int i) => throw new NotSupportedException();
    public override string GetString(int i) => (string)GetValue(i);
    public override decimal GetDecimal(int i) => throw new NotSupportedException();
    public override DateTime GetDateTime(int i) => throw new NotSupportedException();
}
