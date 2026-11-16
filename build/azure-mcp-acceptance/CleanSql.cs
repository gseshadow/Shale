using System;
using System.Collections.Generic;
using System.Data.Common;
using System.Globalization;
using System.Text;
using System.Text.RegularExpressions;

// No connection creation, secrets, logging or SQL rewriting. Used with a pinned
// SqlConnection by the operator entry point and fake readers by offline tests.
public static class CleanSql {
    public static string[] Batches(string sql) {
        var batches = new List<string>(); var batch = new StringBuilder();
        int comment = 0; char quote = '\0';
        foreach (string line in Regex.Split(sql, "(?<=\\n)")) {
            string trimmed = line.Trim();
            if (comment == 0 && quote == '\0') {
                if (trimmed.Equals(":on error exit", StringComparison.OrdinalIgnoreCase)) continue;
                if (trimmed.StartsWith(":") || trimmed.StartsWith("!")) throw new InvalidOperationException("Unsupported SQL directive");
                if (Regex.IsMatch(trimmed, @"^GO(?:\s|$)", RegexOptions.IgnoreCase)) {
                    if (!trimmed.Equals("GO", StringComparison.OrdinalIgnoreCase)) throw new InvalidOperationException("Unsupported GO suffix");
                    if (!string.IsNullOrWhiteSpace(batch.ToString())) batches.Add(batch.ToString());
                    batch.Clear(); continue;
                }
            }
            batch.Append(line);
            for (int i=0; i<line.Length; i++) {
                char c=line[i], n=i+1<line.Length ? line[i+1] : '\0';
                if (comment>0) { if(c=='/' && n=='*'){comment++;i++;} else if(c=='*' && n=='/'){comment--;i++;} continue; }
                if (quote!='\0') { char close=quote=='[' ? ']' : quote; if(c==close){if(n==close)i++;else quote='\0';} continue; }
                if(c=='-' && n=='-') break;
                if(c=='/' && n=='*'){comment++;i++;}
                else if(c=='\'' || c=='"' || c=='[') quote=c;
            }
        }
        if(comment!=0 || quote!='\0') throw new InvalidOperationException("Unterminated SQL token");
        if(!string.IsNullOrWhiteSpace(batch.ToString())) batches.Add(batch.ToString());
        return batches.ToArray();
    }
    // Read and NextResult can throw AFTER preceding rows / success markers.
    // Caller-owned buffer preserves diagnostics already received before the error.
    public static void Drain(DbDataReader reader, List<string> lines) {
        int size=0; foreach (string line in lines) size+=line.Length;
        do {
            int count=reader.FieldCount;
            if(count>0) {
                var names=new string[count]; var dashes=new string[count];
                for(int i=0;i<count;i++){names[i]=reader.GetName(i);dashes[i]="---";}
                lines.Add(string.Join("|",names));lines.Add(string.Join("|",dashes));
            }
            while(reader.Read()) {
                var row=new string[count];
                for(int i=0;i<count;i++) row[i]=reader.IsDBNull(i)?"NULL":Convert.ToString(reader.GetValue(i),CultureInfo.InvariantCulture);
                string value=string.Join("|",row);size+=value.Length;
                if(size>16000000 || lines.Count>100000) throw new InvalidOperationException("SQL result bound exceeded");
                lines.Add(value);
            }
            lines.Add("");
        } while(reader.NextResult());
    }
}
