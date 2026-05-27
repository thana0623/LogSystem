export default function ServiceDetailPage({
  params,
}: {
  params: { name: string };
}) {
  return (
    <div className="p-6">
      <h1 className="text-xl font-semibold">{params.name}</h1>
      <p className="text-muted-foreground mt-2">
        Service detail — TODO
      </p>
    </div>
  );
}
