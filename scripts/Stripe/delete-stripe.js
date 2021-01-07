const axios = require('axios').default
const fs = require('fs')

axios.defaults.baseURL = 'https://api.stripe.com'
axios.defaults.headers.common['Authorization'] = JSON.parse(fs.readFileSync('../../src/test/resources/auth/Stripe/headers.json')).Authorization[0]

const timer = 35
const limit = 200
const service = 'products'

for (let i=0; i<limit; i++) {
    setTimeout(
        function() {
            axios.get(`/v1/${service}?limit=100&offset=${i*100}`)
            .then(function (response) {
                const ids = response.data.data.map(i => i.id)
                console.log(ids)


                let j = 0
                ids.forEach(id => {
                    setTimeout(
                        function() {
                            axios.delete(`/v1/${service}/${id}`).then(() => console.log(`Deleted id ${id}`))
                        },
                        timer*j
                    )
                    j++;
                });
            })
        },
        timer*limit*i
    )
}
