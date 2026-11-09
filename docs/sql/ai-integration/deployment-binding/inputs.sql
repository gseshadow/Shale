/* TEMPLATE: copy the packet to an operator review directory. Edit only this file there.
   Nonsecret values only. No SQLCMD -v interpolation. Escape SQL literals by doubling quotes.
   NULL/0 defaults deliberately refuse installation. Never commit production-filled inputs. */
DECLARE @BindServer nvarchar(max)=NULL, @BindDatabase nvarchar(max)=NULL;
DECLARE @BindOrigin nvarchar(max)=NULL, @BindIssuer nvarchar(max)=NULL, @BindResource nvarchar(max)=NULL;
DECLARE @BindJavaZone nvarchar(max)=NULL, @BindSqlZone nvarchar(max)=NULL;
DECLARE @BindApproved bit=0; -- separately authorized exact binding change
DECLARE @BindOffConfirmed bit=0; -- all effective app controls OFF, AI pools absent/drained
DECLARE @BindEvidenceConfirmed bit=0; -- approved canonical origin/effective JVM mapping, installed SQL, accepted identities
DECLARE @BindWallTimes table(UtcSample datetime2(7) PRIMARY KEY, JavaWallTime datetime2(7) NOT NULL);
/* Paste the 28 INSERT @BindWallTimes rows emitted by BindingTimeZoneProbe.java here.
   They are local table-variable data, not persistent SQL mutations. */
