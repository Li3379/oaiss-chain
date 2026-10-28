FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Prophet 1.1.6 bundles a trimmed cmdstan that omits the top-level makefile,
# which cmdstanpy.validate_cmdstan_path() requires or Prophet init fails with
# "'Prophet' object has no attribute 'stan_backend'". Create the stub makefile.
RUN touch "$(python -c 'import prophet, os; print(os.path.dirname(prophet.__file__))')/stan_model/cmdstan-2.33.1/makefile"

COPY app/ ./app/

# Create a real home dir for mluser (prophet/matplotlib need writable HOME/cache),
# and set MPLCONFIGDIR so matplotlib never tries the unwritable default.
RUN groupadd -r mluser && useradd -r -g mluser -m -d /home/mluser mluser \
    && mkdir -p /tmp/matplotlib && chown -R mluser:mluser /app /tmp/matplotlib
ENV MPLCONFIGDIR=/tmp/matplotlib
USER mluser

EXPOSE 8001

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]
